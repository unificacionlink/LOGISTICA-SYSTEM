package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Ventas de TDEM a GOYDEL y METRIN (DEC-L086). Una sola consulta que devuelve
 * JSON, como el resto del dashboard. El precio es el congelado al despachar
 * (salida_item) o al recibir la entrega directa (recepcion_item).
 */
@Service
public class VentasService {

    /** Una fila por linea vendida, con su cantidad neta y su precio (null = sin precio de venta). */
    private static final String LINEAS = """
            with vendido as (
              select (n.issued_at at time zone 'America/Lima')::date as fecha, n.inventory_note_code as documento,
                     r.request_code as requerimiento, e.code as empresa, c.code as ceco, c.name as ceco_nombre,
                     coalesce(v.internal_variant_code, p.sku_base) as sku, p.name as producto,
                     si.qty - coalesce(si.dest_returned_qty, 0) as cantidad, si.sale_unit_price as precio
                from nota_inventario n
                join salida_item si on si.issue_id = n.issue_id
                join empresa e on e.company_id = n.sale_company_id
                join requerimiento r on r.request_id = n.request_id
                left join ceco c on c.ceco_id = n.destination_ceco_id
                join producto p on p.product_id = si.product_id
                left join producto_variante v on v.variant_id = si.variant_id
               where n.sale_company_id is not null and n.status <> 'ANULADA' and n.issued_at >= :desde
                 and (cast(:empresa as text) is null or e.code = cast(:empresa as text))
              union all
              select (rc.receipt_date at time zone 'America/Lima')::date, o.order_code, r.request_code, e.code,
                     c.code, c.name, coalesce(v.internal_variant_code, p.sku_base), p.name, ri.qty_received,
                     ri.sale_unit_price
                from recepcion rc
                join recepcion_item ri on ri.receipt_id = rc.receipt_id
                join orden_abastecimiento o on o.order_id = rc.order_id
                join ceco c on c.ceco_id = rc.destination_ceco_id
                join empresa e on e.company_id = c.company_id
                join orden_item oi on oi.order_item_id = ri.order_item_id
                join producto p on p.product_id = oi.product_id
                left join producto_variante v on v.variant_id = oi.variant_id
                left join requerimiento_item rqi on rqi.request_item_id = ri.request_item_id
                left join requerimiento r on r.request_id = rqi.request_id
               where rc.receipt_date >= :desde
                 and e.company_id <> (select company_id from almacen where is_group_central and active limit 1)
                 and (cast(:empresa as text) is null or e.code = cast(:empresa as text))
            ),
            lineas as (
              select *, round(cantidad * precio, 2) as total from vendido where cantidad > 0
            )
            """;

    private static final String SQL = LINEAS + """
            select json_build_object(
              'totales', (select coalesce(json_agg(json_build_object('empresa', empresa, 'monto', monto,
                                                                     'lineas', lineas, 'sinPrecio', sin_precio)
                                                   order by empresa), '[]'::json)
                            from (select empresa, coalesce(sum(total), 0) as monto, count(*) as lineas,
                                         count(*) filter (where precio is null) as sin_precio
                                    from lineas group by empresa) x),
              'meses', (select coalesce(json_agg(to_char(m, 'YYYY-MM') order by m), '[]'::json)
                          from generate_series(date_trunc('month', cast(:desde as timestamptz) at time zone 'America/Lima'),
                                               date_trunc('month', now() at time zone 'America/Lima'),
                                               interval '1 month') as m),
              'porMes', (select coalesce(json_agg(json_build_object('empresa', empresa, 'clave', mes, 'detalle', null,
                                                                    'monto', monto) order by empresa, mes), '[]'::json)
                           from (select empresa, to_char(fecha, 'YYYY-MM') as mes, coalesce(sum(total), 0) as monto
                                   from lineas group by 1, 2) x),
              'porCeco', (select coalesce(json_agg(json_build_object('empresa', empresa, 'clave', ceco,
                                                                     'detalle', ceco_nombre, 'monto', monto)
                                                   order by empresa, monto desc), '[]'::json)
                            from (select empresa, ceco, ceco_nombre, coalesce(sum(total), 0) as monto
                                    from lineas group by 1, 2, 3) x),
              'lineas', (select coalesce(json_agg(json_build_object('fecha', fecha, 'documento', documento,
                                                   'requerimiento', requerimiento, 'empresa', empresa, 'ceco', ceco,
                                                   'cecoNombre', ceco_nombre, 'sku', sku, 'producto', producto,
                                                   'cantidad', cantidad, 'precio', precio, 'total', total)
                                                 order by fecha desc, documento desc), '[]'::json)
                           from (select * from lineas order by fecha desc, documento desc limit 100) x)
            )::text
            """;

    private static final String SQL_EXCEL = LINEAS + """
            select * from lineas order by fecha, documento, sku
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public VentasService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public VentasDto obtener(int dias, String empresa, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = filtros(dias, empresa, principal);
        String json = jdbc.queryForObject(SQL, filtros.parametros(), String.class);
        return objectMapper.readValue(json, VentasDto.class);
    }

    /** Todas las lineas del periodo, para el Excel (la pantalla muestra las ultimas 100). */
    public List<VentasDto.Linea> lineas(int dias, String empresa, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = filtros(dias, empresa, principal);
        return jdbc.query(SQL_EXCEL, filtros.parametros(), (rs, i) -> new VentasDto.Linea(
                rs.getObject("fecha", java.time.LocalDate.class), rs.getString("documento"),
                rs.getString("requerimiento"), rs.getString("empresa"), rs.getString("ceco"),
                rs.getString("ceco_nombre"), rs.getString("sku"), rs.getString("producto"),
                rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio"), rs.getBigDecimal("total")));
    }

    /** Lo que TDEM factura a las otras empresas: Logistica, Gerencia y Admin (no el aprobador de CECO). */
    private static FiltrosDashboard filtros(int dias, String empresa, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN, Roles.GERENCIA)) {
            throw ApiException.forbidden("No tienes permiso para ver las ventas entre empresas.");
        }
        return FiltrosDashboard.de(dias, empresa, null, principal);
    }
}
