package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock del almacen central. El saldo disponible (kardex - reservas activas)
 * se calcula aqui en Java, no con una funcion SQL (fn_saldo_stock del diseno
 * de referencia) — mismo patron que DEC-L008.
 *
 * DEC-L018: ya no existe la verificacion manual. Al aprobarse un requerimiento
 * {@link #reservarDisponible} aparta solo lo que hay (RF-009, "Sistema"); el
 * faltante lo toma Compras (RFQ automatica al proveedor habitual).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private static final String CENTRAL = "CENTRAL";

    private final RequerimientoItemRepository requerimientoItemRepository;
    private final AlmacenRepository almacenRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final ReservaStockRepository reservaStockRepository;
    private final ProductoVarianteRepository productoVarianteRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    public StockService(
            RequerimientoItemRepository requerimientoItemRepository,
            AlmacenRepository almacenRepository,
            MovimientoInventarioRepository movimientoInventarioRepository,
            ReservaStockRepository reservaStockRepository,
            ProductoVarianteRepository productoVarianteRepository,
            EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository) {
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.almacenRepository = almacenRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.reservaStockRepository = reservaStockRepository;
        this.productoVarianteRepository = productoVarianteRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar stock.");
        }
    }

    /**
     * Aparta, por item, min(saldo disponible, cantidad solicitada). Lo llama la
     * aprobacion del requerimiento dentro de su propia transaccion. Bloquea la
     * fila del almacen: dos aprobaciones simultaneas no pueden apartar el mismo stock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reservarDisponible(Requerimiento r) {
        Almacen almacen = almacenRepository.bloquear(almacenCentral(r.getEmpresa().getCompanyId()).getWarehouseId())
                .orElseThrow(() -> ApiException.badRequest("La empresa no tiene un almacen central configurado."));
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

    /** Saldos del almacen central de una empresa: fisico (kardex), reservado y disponible. */
    public List<SaldoStockDto> listarSaldos(UUID companyId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Almacen almacen = almacenCentral(companyId);

        Map<UUID, BigDecimal> fisico = new HashMap<>();
        for (Object[] fila : movimientoInventarioRepository.sumarPorVariante(almacen.getWarehouseId())) {
            fisico.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        Map<UUID, BigDecimal> reservado = new HashMap<>();
        for (Object[] fila : reservaStockRepository.sumarReservadoPorVariante(almacen.getWarehouseId())) {
            reservado.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        Set<UUID> ids = new HashSet<>(fisico.keySet());
        ids.addAll(reservado.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProductoVariante> variantes = productoVarianteRepository.findConProductoPorIds(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(ProductoVariante::getVariantId, Function.identity()));

        return ids.stream()
                .filter(variantes::containsKey)
                .map(id -> {
                    ProductoVariante v = variantes.get(id);
                    BigDecimal f = fisico.getOrDefault(id, BigDecimal.ZERO);
                    BigDecimal r = reservado.getOrDefault(id, BigDecimal.ZERO);
                    return new SaldoStockDto(v.getVariantId(), v.getProducto().getSkuBase(), v.getProducto().getName(),
                            v.getAttributeValue(), v.getInternalVariantCode(), f, r, f.subtract(r));
                })
                .filter(s -> s.fisico().signum() != 0 || s.reservado().signum() != 0)
                .sorted(Comparator.comparing(SaldoStockDto::codigoVariante))
                .toList();
    }

    @Transactional
    public SaldoAjustadoDto registrarAjuste(AjusteStockRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);

        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Empresa empresa = empresaRepository.findById(request.companyId())
                .orElseThrow(() -> ApiException.badRequest("Selecciona una empresa valida."));
        Almacen almacen = almacenCentral(empresa.getCompanyId());

        List<ProductoVariante> variantes = productoVarianteRepository.findActivasPorId(List.of(request.variantId()));
        if (variantes.isEmpty()) {
            throw ApiException.badRequest("El producto seleccionado ya no esta disponible.");
        }
        ProductoVariante variante = variantes.get(0);

        MovimientoInventario mov = new MovimientoInventario();
        UUID movId = UUID.randomUUID();
        mov.setInventoryMovementId(movId);
        mov.setEmpresa(empresa);
        mov.setAlmacen(almacen);
        mov.setProducto(variante.getProducto());
        mov.setVariante(variante);
        mov.setMovementType("AJUSTE");
        mov.setQty(request.qty());
        mov.setReferenceType("AJUSTE");
        mov.setReferenceId(movId);
        mov.setCreatedBy(usuario);
        movimientoInventarioRepository.save(mov);

        BigDecimal saldo = saldoDisponible(almacen, variante.getProducto().getProductId(), variante.getVariantId());
        return new SaldoAjustadoDto(variante.getInternalVariantCode(), saldo);
    }

    public List<EmpresaDto> listarEmpresas(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return empresaRepository.findAllByActiveTrueOrderByCode().stream().map(StockMapper::toDto).toList();
    }

    private Almacen almacenCentral(UUID companyId) {
        return almacenRepository.findFirstByEmpresa_CompanyIdAndWarehouseTypeAndActiveTrue(companyId, CENTRAL)
                .orElseThrow(() -> ApiException.badRequest("La empresa no tiene un almacen central configurado."));
    }

    private BigDecimal saldoDisponible(Almacen almacen, UUID productId, UUID variantId) {
        BigDecimal kardex = movimientoInventarioRepository.sumarKardex(almacen.getWarehouseId(), productId, variantId);
        BigDecimal reservado = reservaStockRepository.sumarReservadoActivo(almacen.getWarehouseId(), productId, variantId);
        return kardex.subtract(reservado);
    }
}
