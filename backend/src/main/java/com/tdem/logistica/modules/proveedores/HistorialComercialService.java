package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.modules.proveedores.dto.ComprasProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.DesempenoProveedorDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.FichaProveedorService.cero;
import static com.tdem.logistica.modules.proveedores.FichaProveedorService.fecha;

/**
 * Historial comercial y desempeno del proveedor (DEC-L069), leidos de las
 * tablas de Compras y Almacen sin copiar nada ni tocar sus reglas. Los montos
 * van por moneda (no hay tipo de cambio) y lo que no tiene dato se declara pendiente.
 */
@Service
@Transactional(readOnly = true)
public class HistorialComercialService {

    private static final int LIMITE = 50;
    private static final int PRECIOS_POR_PRODUCTO = 12;

    private final JdbcTemplate jdbc;
    private final ProveedorService proveedores;

    public HistorialComercialService(JdbcTemplate jdbc, ProveedorService proveedores) {
        this.jdbc = jdbc;
        this.proveedores = proveedores;
    }

    public ComprasProveedorDto compras(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(id);

        ComprasProveedorDto.Rfqs rfqs = jdbc.queryForObject("""
                select count(*) filter (where r.status <> 'BORRADOR'),
                       count(*) filter (where r.status <> 'BORRADOR' and c.primera is not null),
                       avg(extract(epoch from c.primera - coalesce(r.sent_at, r.created_at)) / 86400.0)
                         filter (where r.status <> 'BORRADOR' and c.primera is not null)
                  from rfq r
                  left join lateral (select min(coalesce(q.received_at, q.issue_date::timestamptz)) as primera
                                       from cotizacion q where q.rfq_id = r.rfq_id) c on true
                 where r.supplier_id = ?""",
                (rs, n) -> new ComprasProveedorDto.Rfqs(rs.getInt(1), rs.getInt(2), redondear(rs.getBigDecimal(3), 1)),
                id);

        List<ComprasProveedorDto.TotalMoneda> totales = jdbc.query("""
                select currency_code, count(*), sum(total)
                  from orden_abastecimiento
                 where supplier_id = ? and status <> 'ANULADA'
                 group by currency_code order by currency_code""",
                (rs, n) -> new ComprasProveedorDto.TotalMoneda(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)), id);

        List<ComprasProveedorDto.Cotizacion> cotizaciones = jdbc.query("""
                select q.quotation_id, r.rfq_code, q.supplier_quote_number,
                       coalesce(q.received_at, q.issue_date::timestamptz) as recibida,
                       q.currency_code, q.total, q.lead_days, q.expiration_date, q.status
                  from cotizacion q join rfq r on r.rfq_id = q.rfq_id
                 where q.supplier_id = ?
                 order by recibida desc nulls last
                 limit ?""",
                (rs, n) -> new ComprasProveedorDto.Cotizacion(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), instante(rs, 4), rs.getString(5), rs.getBigDecimal(6),
                        (Integer) rs.getObject(7), fecha(rs, "expiration_date"), rs.getString(9)),
                id, LIMITE);

        List<ComprasProveedorDto.Orden> ordenes = jdbc.query("""
                select o.order_id, o.order_code, e.code, o.issue_date, o.status, o.currency_code, o.total,
                       o.payment_status, o.approved_at, o.released_at,
                       (select min(rc.receipt_date) from recepcion rc where rc.order_id = o.order_id)
                  from orden_abastecimiento o join empresa e on e.company_id = o.company_id
                 where o.supplier_id = ?
                 order by o.created_at desc
                 limit ?""",
                (rs, n) -> new ComprasProveedorDto.Orden(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        fecha(rs, "issue_date"), rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getString(8),
                        instante(rs, 9), instante(rs, 10), instante(rs, 11)),
                id, LIMITE);

        return new ComprasProveedorDto(rfqs, totales, cotizaciones, ordenes, productos(id));
    }

    /** Productos comprados (OC no anuladas) con su historial de precio unitario, por moneda. */
    private List<ComprasProveedorDto.Producto> productos(UUID id) {
        Map<String, List<ComprasProveedorDto.Precio>> precios = new LinkedHashMap<>();
        Map<String, ComprasProveedorDto.Producto> base = new LinkedHashMap<>();
        jdbc.query("""
                select oi.product_id, p.sku_base, p.name, v.internal_variant_code, o.currency_code,
                       oi.unit_cost, oi.qty_ordered, o.issue_date, o.order_code, o.order_id
                  from orden_item oi
                  join orden_abastecimiento o on o.order_id = oi.order_id
                  join producto p on p.product_id = oi.product_id
                  left join producto_variante v on v.variant_id = oi.variant_id
                 where o.supplier_id = ? and o.status <> 'ANULADA'
                 order by o.issue_date desc, o.created_at desc""",
                rs -> {
                    String clave = rs.getString(1) + "|" + rs.getString(4) + "|" + rs.getString(5);
                    List<ComprasProveedorDto.Precio> lista = precios.computeIfAbsent(clave, k -> new ArrayList<>());
                    if (lista.size() < PRECIOS_POR_PRODUCTO) {
                        lista.add(new ComprasProveedorDto.Precio(fecha(rs, "issue_date"), rs.getBigDecimal(6),
                                rs.getString(9)));
                    }
                    ComprasProveedorDto.Producto previo = base.get(clave);
                    BigDecimal precio = rs.getBigDecimal(6);
                    BigDecimal cantidad = rs.getBigDecimal(7);
                    if (previo == null) {
                        base.put(clave, new ComprasProveedorDto.Producto(rs.getObject(1, UUID.class), rs.getString(2),
                                rs.getString(3), rs.getString(4), rs.getString(5), 1, cantidad, precio, precio, precio,
                                fecha(rs, "issue_date"), List.of()));
                    } else {
                        base.put(clave, new ComprasProveedorDto.Producto(previo.productId(), previo.skuBase(),
                                previo.nombre(), previo.variante(), previo.moneda(), previo.ordenes() + 1,
                                previo.cantidad().add(cantidad), previo.ultimoPrecio(),
                                previo.precioMinimo().min(precio), previo.precioMaximo().max(precio),
                                previo.ultimaCompra(), List.of()));
                    }
                }, id);
        List<ComprasProveedorDto.Producto> resultado = new ArrayList<>();
        base.forEach((clave, p) -> resultado.add(new ComprasProveedorDto.Producto(p.productId(), p.skuBase(),
                p.nombre(), p.variante(), p.moneda(), p.ordenes(), p.cantidad(), p.ultimoPrecio(), p.precioMinimo(),
                p.precioMaximo(), p.ultimaCompra(), precios.get(clave))));
        return resultado;
    }

    public DesempenoProveedorDto desempeno(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(id);

        // Ordenado vs recibido (solo cantidad aceptada, DEC-L017) y plazo real hasta la primera recepcion.
        Object[] ordenes = jdbc.queryForObject("""
                with oc as (
                  select o.order_id,
                         coalesce(o.released_at, o.approved_at, o.created_at) as desde,
                         q.lead_days,
                         (select sum(oi.qty_ordered) from orden_item oi where oi.order_id = o.order_id) as ordenado,
                         (select sum(ri.qty_received) from recepcion_item ri
                            join orden_item oi on oi.order_item_id = ri.order_item_id
                           where oi.order_id = o.order_id) as recibido,
                         (select min(rc.receipt_date) from recepcion rc where rc.order_id = o.order_id) as primera
                    from orden_abastecimiento o
                    left join cotizacion q on q.quotation_id = o.quotation_id
                   where o.supplier_id = ? and o.status not in ('ANULADA', 'POR_APROBAR')
                )
                select count(*),
                       count(*) filter (where primera is not null),
                       coalesce(sum(ordenado), 0),
                       coalesce(sum(recibido), 0),
                       avg(extract(epoch from primera - desde) / 86400.0) filter (where primera is not null),
                       count(*) filter (where primera is not null and lead_days is not null),
                       count(*) filter (where primera is not null and lead_days is not null
                                          and primera <= desde + make_interval(days => lead_days))
                  from oc""",
                (rs, n) -> new Object[]{rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getInt(6), rs.getInt(7)}, id);
        BigDecimal ordenado = cero((BigDecimal) ordenes[2]);
        BigDecimal recibido = cero((BigDecimal) ordenes[3]);
        BigDecimal atencion = ordenado.signum() == 0 ? null
                : recibido.multiply(BigDecimal.valueOf(100)).divide(ordenado, 1, RoundingMode.HALF_UP);

        int[] recepciones = jdbc.queryForObject("""
                select count(*), count(*) filter (where rc.status = 'OBSERVADA')
                  from recepcion rc join orden_abastecimiento o on o.order_id = rc.order_id
                 where o.supplier_id = ?""",
                (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, id);

        List<DesempenoProveedorDto.PorTipo> porTipo = new ArrayList<>();
        int[] totales = new int[3];
        BigDecimal[] diasCierre = new BigDecimal[1];
        jdbc.query("""
                select i.issue_type, count(*),
                       count(*) filter (where i.status <> 'CERRADA'),
                       count(*) filter (where i.status = 'CERRADA'),
                       sum(extract(epoch from i.closed_at - i.created_at) / 86400.0) filter (where i.closed_at is not null),
                       count(*) filter (where i.closed_at is not null)
                  from incidencia_recepcion i
                  join recepcion rc on rc.receipt_id = i.receipt_id
                  join orden_abastecimiento o on o.order_id = rc.order_id
                 where o.supplier_id = ?
                 group by i.issue_type order by count(*) desc""",
                rs -> {
                    porTipo.add(new DesempenoProveedorDto.PorTipo(rs.getString(1), rs.getInt(2)));
                    totales[0] += rs.getInt(2);
                    totales[1] += rs.getInt(3);
                    totales[2] += rs.getInt(4);
                    BigDecimal suma = rs.getBigDecimal(5);
                    if (suma != null) {
                        diasCierre[0] = cero(diasCierre[0]).add(suma);
                    }
                }, id);
        Integer cerradasConFecha = jdbc.queryForObject("""
                select count(*) from incidencia_recepcion i
                  join recepcion rc on rc.receipt_id = i.receipt_id
                  join orden_abastecimiento o on o.order_id = rc.order_id
                 where o.supplier_id = ? and i.closed_at is not null""", Integer.class, id);
        BigDecimal promedioCierre = diasCierre[0] == null || cerradasConFecha == null || cerradasConFecha == 0 ? null
                : diasCierre[0].divide(BigDecimal.valueOf(cerradasConFecha), 1, RoundingMode.HALF_UP);

        int[] cotizaciones = jdbc.queryForObject("""
                select count(*), count(*) filter (where status = 'SELECCIONADA') from cotizacion where supplier_id = ?""",
                (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, id);

        List<DesempenoProveedorDto.Pendiente> pendientes = List.of(
                new DesempenoProveedorDto.Pendiente("Cumplimiento de fecha comprometida",
                        "La orden de compra no registra una fecha de entrega comprometida; el plazo se compara solo "
                                + "contra el ofrecido en la cotización (PD-14)."),
                new DesempenoProveedorDto.Pendiente("Montos en una sola moneda",
                        "No hay tipo de cambio registrado: los montos se muestran por moneda."),
                new DesempenoProveedorDto.Pendiente("Diferencias en destino",
                        "Las diferencias de la recepción en destino no se atribuyen al proveedor."));

        return new DesempenoProveedorDto((Integer) ordenes[0], (Integer) ordenes[1], ordenado, recibido, atencion,
                recepciones[0], recepciones[1],
                new DesempenoProveedorDto.Incidencias(totales[0], totales[1], totales[2], promedioCierre, porTipo),
                redondear((BigDecimal) ordenes[4], 1),
                new DesempenoProveedorDto.Plazo((Integer) ordenes[5], (Integer) ordenes[6]),
                cotizaciones[0], cotizaciones[1], pendientes);
    }

    private static BigDecimal redondear(BigDecimal v, int decimales) {
        return v == null ? null : v.setScale(decimales, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime instante(ResultSet rs, int columna) throws SQLException {
        Timestamp t = rs.getTimestamp(columna);
        return t != null ? t.toInstant().atOffset(ZoneOffset.UTC) : null;
    }
}
