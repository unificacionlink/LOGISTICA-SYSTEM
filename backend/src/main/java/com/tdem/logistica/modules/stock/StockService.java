package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

/**
 * Stock del almacen central del grupo (DEC-L045: lo maneja TDEM y atiende a
 * las tres empresas). El saldo disponible (kardex - reservas activas) se
 * calcula aqui, no con una funcion SQL (mismo patron que DEC-L008).
 *
 * DEC-L018: al aprobarse un requerimiento {@link #reservarDisponible} aparta
 * solo lo que hay; el faltante lo toma Compras (DEC-L039).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    /** Todos los SKU activos (o con saldo) con su fisico, reservado y minimo, en una sola consulta. */
    private static final String SQL_SALDOS = """
            with fis as (
                select variant_id, sum(qty) q, max(created_at) ult
                  from movimiento_inventario where warehouse_id = ? group by variant_id),
            res as (
                select variant_id, sum(qty_reserved) q
                  from reserva_stock where warehouse_id = ? and status = 'RESERVADA' group by variant_id)
            select v.variant_id, p.product_id, p.sku_base, p.name, v.attribute_value, v.internal_variant_code,
                   t.product_type_id, t.name, f.family_id, f.name, u.code,
                   coalesce(fis.q, 0), coalesce(res.q, 0), sm.min_qty, fis.ult
              from producto_variante v
              join producto p on p.product_id = v.product_id
              join familia_producto f on f.family_id = p.family_id
              join tipo_producto t on t.product_type_id = f.product_type_id
              join unidad_medida u on u.uom_id = p.base_uom_id
              left join fis on fis.variant_id = v.variant_id
              left join res on res.variant_id = v.variant_id
              left join stock_minimo sm on sm.warehouse_id = ? and sm.variant_id = v.variant_id
             where ((v.active and p.active) or coalesce(fis.q, 0) <> 0 or coalesce(res.q, 0) <> 0)
            """;

    private static final RowMapper<SaldoStockDto> SALDO = (rs, n) -> {
        BigDecimal fisico = rs.getBigDecimal(12);
        BigDecimal reservado = rs.getBigDecimal(13);
        Timestamp ult = rs.getTimestamp(15);
        return new SaldoStockDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8),
                rs.getObject(9, UUID.class), rs.getString(10), rs.getString(11), fisico, reservado,
                fisico.subtract(reservado), rs.getBigDecimal(14), ult != null ? ult.toInstant() : null);
    };

    private final RequerimientoItemRepository requerimientoItemRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final ReservaStockRepository reservaStockRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlmacenCentral almacenCentral;
    private final JdbcTemplate jdbc;

    public StockService(
            RequerimientoItemRepository requerimientoItemRepository,
            MovimientoInventarioRepository movimientoInventarioRepository,
            ReservaStockRepository reservaStockRepository,
            ProductoVarianteRepository productoVarianteRepository,
            UsuarioRepository usuarioRepository,
            AlmacenCentral almacenCentral,
            JdbcTemplate jdbc) {
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.reservaStockRepository = reservaStockRepository;
        this.productoVarianteRepository = productoVarianteRepository;
        this.usuarioRepository = usuarioRepository;
        this.almacenCentral = almacenCentral;
        this.jdbc = jdbc;
    }

    /**
     * Disponible (fisico - reservado) por variante en el almacen central, en una
     * consulta. Una variante sin movimientos no aparece (= 0). Vacio si no hay
     * almacen central. Lo ve quien pide al armar su requerimiento.
     */
    public Map<UUID, BigDecimal> disponiblesCentral() {
        Map<UUID, BigDecimal> out = new HashMap<>();
        almacenCentral.buscar().ifPresent(a -> jdbc.query("""
                with fis as (
                    select variant_id, sum(qty) q
                      from movimiento_inventario where warehouse_id = ? group by variant_id),
                res as (
                    select variant_id, sum(qty_reserved) q
                      from reserva_stock where warehouse_id = ? and status = 'RESERVADA' group by variant_id)
                select fis.variant_id, fis.q - coalesce(res.q, 0)
                  from fis left join res on res.variant_id = fis.variant_id""",
                rs -> {
                    out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2).max(BigDecimal.ZERO));
                }, a.getWarehouseId(), a.getWarehouseId()));
        return out;
    }

    static void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar stock.");
        }
    }

    /**
     * Aparta, por item, min(saldo disponible, cantidad solicitada) del almacen
     * central del grupo, sea cual sea la empresa del requerimiento (DEC-L045).
     * Lo llama la aprobacion dentro de su transaccion. Bloquea la fila del
     * almacen: dos aprobaciones simultaneas no pueden apartar el mismo stock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reservarDisponible(Requerimiento r) {
        // DEC-L054: si el solicitante sugiere la caja chica del proyecto no se aparta
        // stock; Logistica decide en Necesidades si usa el almacen o la caja chica.
        if (r.isPettyCashSuggested()) {
            return;
        }
        Almacen almacen = almacenCentral.bloquear();
        for (RequerimientoItem item : requerimientoItemRepository.findByRequerimiento(r.getRequestId())) {
            UUID variantId = item.getVariante() != null ? item.getVariante().getVariantId() : null;
            BigDecimal aReservar = saldoDisponible(almacen, item.getProducto().getProductId(), variantId)
                    .min(item.getQtyRequested());
            if (aReservar.signum() <= 0) {
                continue;
            }
            ReservaStock reserva = new ReservaStock();
            reserva.setReservationId(UUID.randomUUID());
            reserva.setRequerimientoItem(item);
            reserva.setAlmacen(almacen);
            reserva.setProducto(item.getProducto());
            reserva.setVariante(item.getVariante());
            reserva.setQtyReserved(aReservar);
            reserva.setStatus("RESERVADA");
            reservaStockRepository.save(reserva);
        }
    }

    /**
     * Aparta del almacen central para el item hasta {@code maximo} (Logistica
     * elige "Usar stock" en una necesidad, DEC-L054). Devuelve lo reservado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reservarParaItem(RequerimientoItem item, BigDecimal maximo) {
        Almacen almacen = almacenCentral.bloquear();
        UUID variantId = item.getVariante() != null ? item.getVariante().getVariantId() : null;
        BigDecimal aReservar = saldoDisponible(almacen, item.getProducto().getProductId(), variantId).min(maximo);
        if (aReservar.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        ReservaStock reserva = new ReservaStock();
        reserva.setReservationId(UUID.randomUUID());
        reserva.setRequerimientoItem(item);
        reserva.setAlmacen(almacen);
        reserva.setProducto(item.getProducto());
        reserva.setVariante(item.getVariante());
        reserva.setQtyReserved(aReservar);
        reserva.setStatus("RESERVADA");
        reservaStockRepository.save(reserva);
        return aReservar;
    }

    /** Disponible (fisico - reservado) del almacen central por variante, en una consulta. */
    public Map<UUID, BigDecimal> disponibles(Collection<UUID> variantIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (variantIds.isEmpty()) {
            return out;
        }
        UUID almacen = almacenCentral.obtener().getWarehouseId();
        String marcas = String.join(",", Collections.nCopies(variantIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(almacen);
        args.add(almacen);
        args.addAll(variantIds);
        jdbc.query("""
                select v.variant_id,
                       coalesce((select sum(m.qty) from movimiento_inventario m
                                  where m.warehouse_id = ? and m.variant_id = v.variant_id), 0)
                     - coalesce((select sum(r.qty_reserved) from reserva_stock r
                                  where r.warehouse_id = ? and r.variant_id = v.variant_id and r.status = 'RESERVADA'), 0)
                  from producto_variante v where v.variant_id in (%s)""".formatted(marcas), rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2).max(BigDecimal.ZERO));
        }, args.toArray());
        return out;
    }

    /** Todo el catalogo con su stock: la pantalla filtra (con stock, sin stock, bajo minimo). */
    public List<SaldoStockDto> listarSaldos(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return saldos(almacenCentral.obtener().getWarehouseId());
    }

    /** Saldos del almacen, para la pantalla y la carga por Excel. */
    List<SaldoStockDto> saldos(UUID warehouseId) {
        return jdbc.query(SQL_SALDOS + " order by v.internal_variant_code", SALDO, warehouseId, warehouseId, warehouseId);
    }

    private SaldoStockDto saldo(UUID warehouseId, UUID variantId) {
        return jdbc.query(SQL_SALDOS + " and v.variant_id = ?", SALDO, warehouseId, warehouseId, warehouseId, variantId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
    }

    /** Kardex del SKU: cada entrada, salida y ajuste con su documento, motivo y usuario. */
    public KardexDto kardex(UUID variantId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        UUID almacen = almacenCentral.obtener().getWarehouseId();
        SaldoStockDto saldo = saldo(almacen, variantId);
        record Fila(Timestamp fecha, String tipo, String origen, BigDecimal qty, String referencia, String empresa,
                    String motivo, String usuario) {
        }
        List<Fila> filas = jdbc.query("""
                select m.created_at, m.movement_type, m.reference_type, m.qty,
                       case m.reference_type
                         when 'RECEPCION' then (select o.order_code || ' · recepcion ' || rc.receipt_number
                                                  from recepcion rc join orden_abastecimiento o on o.order_id = rc.order_id
                                                 where rc.receipt_id = m.reference_id)
                         when 'SALIDA' then (select ni.inventory_note_code from nota_inventario ni
                                              where ni.issue_id = m.reference_id)
                         when 'CAJA_CHICA' then (select cc.petty_cash_code from caja_chica_ref cc
                                                  where cc.petty_cash_ref_id = m.reference_id)
                         when 'CARGA_STOCK' then (select cs.code from carga_stock cs where cs.stock_load_id = m.reference_id)
                       end,
                       e.code, m.notes, u.display_name
                  from movimiento_inventario m
                  join empresa e on e.company_id = m.company_id
                  join usuario u on u.user_id = m.created_by
                 where m.warehouse_id = ? and m.variant_id = ?
                 order by m.created_at, m.inventory_movement_id""",
                (rs, n) -> new Fila(rs.getTimestamp(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
                almacen, variantId);
        List<KardexDto.Movimiento> movimientos = new ArrayList<>(filas.size());
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Fila f : filas) {
            acumulado = acumulado.add(f.qty());
            movimientos.add(new KardexDto.Movimiento(f.fecha().toInstant(), f.tipo(), f.origen(), f.qty(), acumulado,
                    f.referencia(), f.empresa(), f.motivo(), f.usuario()));
        }
        Collections.reverse(movimientos);
        return new KardexDto(saldo, movimientos);
    }

    /** Ajuste manual con signo y motivo; no deja el fisico en negativo. */
    @Transactional
    public SaldoStockDto registrarAjuste(AjusteStockRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (request.qty().signum() == 0) {
            throw ApiException.badRequest("La cantidad no puede ser cero.");
        }
        if (request.qty().stripTrailingZeros().scale() > 3) {
            throw ApiException.badRequest("Usa como maximo 3 decimales.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Almacen almacen = almacenCentral.bloquear();
        ProductoVariante variante = productoVarianteRepository.findActivasPorId(List.of(request.variantId())).stream()
                .findFirst().orElseThrow(() -> ApiException.badRequest("El producto seleccionado ya no esta disponible."));

        BigDecimal fisico = movimientoInventarioRepository.sumarKardex(almacen.getWarehouseId(),
                variante.getProducto().getProductId(), variante.getVariantId());
        if (fisico.add(request.qty()).signum() < 0) {
            throw ApiException.badRequest("La salida supera el stock fisico (" + fisico.stripTrailingZeros().toPlainString() + ").");
        }

        MovimientoInventario mov = new MovimientoInventario();
        UUID movId = UUID.randomUUID();
        mov.setInventoryMovementId(movId);
        mov.setEmpresa(almacen.getEmpresa());
        mov.setAlmacen(almacen);
        mov.setProducto(variante.getProducto());
        mov.setVariante(variante);
        mov.setMovementType("AJUSTE");
        mov.setQty(request.qty());
        mov.setReferenceType("AJUSTE");
        mov.setReferenceId(movId);
        mov.setNotes(request.motivo().trim());
        mov.setCreatedBy(usuario);
        movimientoInventarioRepository.saveAndFlush(mov);
        return saldo(almacen.getWarehouseId(), variante.getVariantId());
    }

    /** Fija o quita (null) el stock minimo del SKU. */
    @Transactional
    public SaldoStockDto fijarMinimo(UUID variantId, BigDecimal minimo, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        UUID almacen = almacenCentral.obtener().getWarehouseId();
        if (productoVarianteRepository.findActivasPorId(List.of(variantId)).isEmpty()) {
            throw ApiException.badRequest("El producto seleccionado ya no esta disponible.");
        }
        guardarMinimo(almacen, variantId, minimo, principal.userId());
        return saldo(almacen, variantId);
    }

    void guardarMinimo(UUID warehouseId, UUID variantId, BigDecimal minimo, UUID userId) {
        if (minimo == null) {
            jdbc.update("delete from stock_minimo where warehouse_id = ? and variant_id = ?", warehouseId, variantId);
            return;
        }
        jdbc.update("""
                insert into stock_minimo (warehouse_id, variant_id, min_qty, updated_by, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (warehouse_id, variant_id)
                do update set min_qty = excluded.min_qty, updated_by = excluded.updated_by, updated_at = now()""",
                warehouseId, variantId, minimo, userId);
    }

    private BigDecimal saldoDisponible(Almacen almacen, UUID productId, UUID variantId) {
        BigDecimal kardex = movimientoInventarioRepository.sumarKardex(almacen.getWarehouseId(), productId, variantId);
        BigDecimal reservado = reservaStockRepository.sumarReservadoActivo(almacen.getWarehouseId(), productId, variantId);
        return kardex.subtract(reservado);
    }
}
