package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Pestana Demanda (DEC-L083): el dashboard operativo de la V38.7 con datos del
 * servidor. Cuenta lineas de requerimiento enviadas (no BORRADOR) por fecha de
 * envio; la matriz ABC usa lo despachado del almacen central. Todo en una
 * consulta que devuelve JSON (la BD esta lejos, DEC-L020).
 */
@Service
@Transactional(readOnly = true)
public class DemandaService {

    /** Lineas del periodo con sus filtros; lo comparten la pantalla y el Excel. */
    private static final String LINEAS = """
            with lineas as (
              select r.request_id, r.request_code, r.status,
                     (coalesce(r.submitted_at, r.created_at) at time zone 'America/Lima')::date as fecha,
                     e.code as empresa, c.code as ceco, ri.qty_requested as qty,
                     p.product_id, coalesce(v.internal_variant_code, p.sku_base) as sku, p.sku_base, p.name as producto,
                     coalesce(t.name, 'Sin clasificar') as familia, coalesce(f.name, 'Sin clasificar') as subfamilia
                from requerimiento_item ri
                join requerimiento r on r.request_id = ri.request_id
                join empresa e on e.company_id = r.company_id
                left join ceco c on c.ceco_id = r.ceco_id
                join producto p on p.product_id = ri.product_id
                left join producto_variante v on v.variant_id = ri.variant_id
                left join tipo_producto t on t.product_type_id = p.product_type_id
                left join familia_producto f on f.family_id = p.family_id
               where r.status <> 'BORRADOR'
                 and coalesce(r.submitted_at, r.created_at) >= :desde
                 and (cast(:empresa as text) is null or e.code = cast(:empresa as text))
                 and (cast(:tipo as uuid) is null or p.product_type_id = cast(:tipo as uuid))
                 and (cast(:familia as uuid) is null or p.family_id = cast(:familia as uuid))
            )
            """;

    /*
     * costo   = ultimo costo unitario NETO (sin IGV: el subtotal de la OC repartido entre sus lineas,
     *           DEC-L079) de cada producto en una OC en soles aprobada.
     * consumo = unidades despachadas del almacen o entregadas directo por el proveedor en el periodo.
     * abc     = consumo valorizado y acumulado de mayor a menor; la clase se decide por lo acumulado
     *           ANTES del producto (el que cruza el 80% todavia es A).
     */
    private static final String SQL = LINEAS + """
            , costo as (
              select distinct on (product_id) product_id, unit_neto
                from (select oi.product_id, o.approved_at,
                             oi.unit_cost * o.subtotal
                               / nullif(sum(coalesce(oi.line_total, 0)) over (partition by o.order_id), 0) as unit_neto
                        from orden_item oi
                        join orden_abastecimiento o on o.order_id = oi.order_id
                       where o.currency_code = 'PEN' and o.approved_at is not null and o.status <> 'ANULADA') x
               where product_id is not null and unit_neto is not null
               order by product_id, approved_at desc
            ),
            consumo as (
              select x.product_id, sum(x.qty) as unidades
                from (select si.product_id, si.qty, s.company_id
                        from salida_item si
                        join salida s on s.issue_id = si.issue_id
                       where s.status not in ('BORRADOR', 'ANULADA') and s.issued_at >= :desde
                      union all
                      -- entrega directa del proveedor al CECO (DEC-L084): tambien es consumo
                      select oi.product_id, rci.qty_received, o.company_id
                        from recepcion_item rci
                        join recepcion rc on rc.receipt_id = rci.receipt_id
                        join orden_item oi on oi.order_item_id = rci.order_item_id
                        join orden_abastecimiento o on o.order_id = rc.order_id
                       where rc.destination_ceco_id is not null and rc.receipt_date >= :desde) x
                join empresa e on e.company_id = x.company_id
                join producto p on p.product_id = x.product_id
               where (cast(:empresa as text) is null or e.code = cast(:empresa as text))
                 and (cast(:tipo as uuid) is null or p.product_type_id = cast(:tipo as uuid))
                 and (cast(:familia as uuid) is null or p.family_id = cast(:familia as uuid))
               group by x.product_id
            ),
            valorizado as (
              select p.sku_base as sku, p.name as nombre, c.unidades, round(c.unidades * k.unit_neto, 2) as valor
                from consumo c
                join producto p on p.product_id = c.product_id
                join costo k on k.product_id = c.product_id
            ),
            abc as (
              select v.*,
                     sum(valor) over (order by valor desc, sku rows between unbounded preceding and current row)
                       - valor as previo,
                     sum(valor) over () as total
                from valorizado v
               where valor > 0
            ),
            clasificado as (
              select sku, nombre, unidades, valor, total,
                     round(100 * valor / total, 1) as pct,
                     case when previo < 0.80 * total then 'A' when previo < 0.95 * total then 'B' else 'C' end as clase
                from abc
            )
            select json_build_object(
              'requerimientos', (select count(distinct request_id) from lineas),
              'lineas', (select count(*) from lineas),
              'skus', (select count(distinct sku_base) from lineas),
              'familias', (select count(distinct familia) from lineas),
              'porFamilia', (select coalesce(json_agg(json_build_object('clave', familia, 'lineas', n, 'unidades', u)
                                                      order by n desc, familia), '[]'::json)
                               from (select familia, count(*) as n, sum(qty) as u from lineas group by familia) x),
              'porMes', (select coalesce(json_agg(json_build_object('clave', to_char(m.mes, 'YYYY-MM'),
                                                                    'lineas', coalesce(x.n, 0), 'unidades', coalesce(x.u, 0))
                                                  order by m.mes), '[]'::json)
                           from generate_series(date_trunc('month', cast(:desde as timestamptz) at time zone 'America/Lima'),
                                                date_trunc('month', now() at time zone 'America/Lima'),
                                                interval '1 month') as m(mes)
                           left join (select date_trunc('month', fecha::timestamp) as mes, count(*) as n, sum(qty) as u
                                        from lineas group by 1) x on x.mes = m.mes),
              'productos', (select coalesce(json_agg(json_build_object('sku', sku_base, 'nombre', producto,
                                                                       'lineas', n, 'unidades', u)
                                                     order by n desc, u desc, sku_base), '[]'::json)
                              from (select sku_base, producto, count(*) as n, sum(qty) as u
                                      from lineas group by sku_base, producto
                                     order by count(*) desc, sum(qty) desc, sku_base limit 10) x),
              'porEstado', (select coalesce(json_agg(json_build_object('clave', status, 'lineas', n, 'unidades', null)
                                                     order by n desc, status), '[]'::json)
                              from (select status, count(distinct request_id) as n from lineas group by status) x),
              'abc', json_build_object(
                'total', (select coalesce(max(total), 0) from clasificado),
                'clases', (select json_agg(json_build_object('clase', k.clase,
                                                             'productos', (select count(*) from clasificado c where c.clase = k.clase),
                                                             'valor', (select coalesce(sum(valor), 0) from clasificado c where c.clase = k.clase))
                                           order by k.clase)
                             from (values ('A'), ('B'), ('C')) as k(clase)),
                'productos', (select coalesce(json_agg(json_build_object('sku', sku, 'nombre', nombre, 'unidades', unidades,
                                                                         'valor', valor, 'pct', pct, 'clase', clase)
                                                       order by valor desc, sku), '[]'::json)
                                from (select * from clasificado order by valor desc, sku limit 15) x),
                'sinCosto', (select count(*) from consumo c where not exists (select 1 from costo k where k.product_id = c.product_id))
              ),
              'totalDetalle', (select count(*) from lineas),
              'detalle', (select coalesce(json_agg(json_build_object('requerimiento', request_code, 'empresa', empresa,
                                                                     'ceco', ceco, 'fecha', fecha, 'estado', status,
                                                                     'familia', familia, 'subfamilia', subfamilia,
                                                                     'sku', sku, 'producto', producto, 'cantidad', qty)
                                                   order by fecha desc, request_code desc, sku), '[]'::json)
                            from (select * from lineas order by fecha desc, request_code desc, sku limit 100) x)
            )::text
            """;

    private static final String SQL_EXCEL = LINEAS + """
            select request_code, empresa, ceco, fecha, status, familia, subfamilia, sku, producto, qty
              from lineas
             order by fecha desc, request_code desc, sku
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DemandaService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DemandaDto obtener(int dias, String empresa, String familia, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = FiltrosDashboard.de(dias, empresa, familia, principal);
        String json = jdbc.queryForObject(SQL, filtros.parametros(), String.class);
        return objectMapper.readValue(json, DemandaDto.class);
    }

    /** Todas las lineas del periodo (la pantalla muestra las ultimas 100). */
    public List<DemandaDto.Linea> lineas(int dias, String empresa, String familia, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = FiltrosDashboard.de(dias, empresa, familia, principal);
        return jdbc.query(SQL_EXCEL, filtros.parametros(), (rs, i) -> new DemandaDto.Linea(
                rs.getString("request_code"), rs.getString("empresa"), rs.getString("ceco"),
                rs.getObject("fecha", java.time.LocalDate.class), rs.getString("status"),
                rs.getString("familia"), rs.getString("subfamilia"), rs.getString("sku"),
                rs.getString("producto"), rs.getBigDecimal("qty")));
    }
}
