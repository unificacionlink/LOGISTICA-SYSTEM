package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Pestana Gasto (DEC-L083): cuanto se compro, para quien y de que. Una sola
 * consulta que devuelve JSON (DEC-L020).
 */
@Service
@Transactional(readOnly = true)
public class GastoService {

    /*
     * ordenes = OC aprobadas en el periodo (fecha de aprobacion), no anuladas, de la empresa filtrada.
     * lineas  = cada linea con su parte del VALOR TOTAL sin IGV: el subtotal de la OC repartido por
     *           el peso de la linea (las lineas guardan lo cotizado, con o sin IGV, DEC-L079).
     * reparto = a que CECO va cada linea: el del REQ si la linea lo trae; si sale de un consolidado,
     *           proporcional a las necesidades de compra que agrupa (DEC-L039); si no, el CECO de la
     *           OC (OC directa, DEC-L080) o ninguno (compra para stock).
     */
    private static final String SQL = """
            with ordenes as (
              select o.order_id, e.code as empresa, o.supplier_id, o.currency_code as moneda, o.subtotal,
                     (o.approved_at at time zone 'America/Lima') as aprobada, o.ceco_id
                from orden_abastecimiento o
                join empresa e on e.company_id = o.company_id
               where o.approved_at >= :desde and o.status <> 'ANULADA'
                 and (cast(:empresa as text) is null or e.code = cast(:empresa as text))
            ),
            lineas as (
              select oi.order_item_id, oi.product_id, oi.request_item_id, oi.consolidation_id,
                     o.moneda, o.ceco_id as oc_ceco,
                     coalesce(o.subtotal * coalesce(oi.line_total, 0)
                                / nullif(sum(coalesce(oi.line_total, 0)) over (partition by oi.order_id), 0), 0) as neto
                from orden_item oi
                join ordenes o on o.order_id = oi.order_id
            ),
            reparto as (
              select l.moneda, r.ceco_id, l.neto as monto
                from lineas l
                join requerimiento_item ri on ri.request_item_id = l.request_item_id
                join requerimiento r on r.request_id = ri.request_id
              union all
              select l.moneda, r.ceco_id, l.neto * n.qty / sum(n.qty) over (partition by l.order_item_id)
                from lineas l
                join necesidad_compra n on n.consolidation_id = l.consolidation_id and n.status = 'COMPRA'
                join requerimiento_item ri on ri.request_item_id = n.request_item_id
                join requerimiento r on r.request_id = ri.request_id
               where l.request_item_id is null
              union all
              select l.moneda, l.oc_ceco, l.neto
                from lineas l
               where l.request_item_id is null
                 and not exists (select 1 from necesidad_compra n
                                  where n.consolidation_id = l.consolidation_id and n.status = 'COMPRA')
            ),
            por_ceco as (
              select x.moneda, c.code as clave, c.name as detalle, sum(x.monto) as monto,
                     row_number() over (partition by x.moneda order by sum(x.monto) desc, c.code nulls last) as n
                from reparto x
                left join ceco c on c.ceco_id = x.ceco_id
               group by x.moneda, c.code, c.name
            ),
            por_familia as (
              select l.moneda, coalesce(t.name, case when l.product_id is null then 'Servicios' else 'Sin clasificar' end) as clave,
                     sum(l.neto) as monto,
                     row_number() over (partition by l.moneda order by sum(l.neto) desc) as n
                from lineas l
                left join producto p on p.product_id = l.product_id
                left join tipo_producto t on t.product_type_id = p.product_type_id
               group by l.moneda, 2
            ),
            por_proveedor as (
              select o.moneda, p.legal_name as clave, sum(o.subtotal) as monto, count(*) as ordenes,
                     row_number() over (partition by o.moneda order by sum(o.subtotal) desc, p.legal_name) as n
                from ordenes o
                join proveedor p on p.supplier_id = o.supplier_id
               group by o.moneda, p.legal_name
            )
            select json_build_object(
              'monedas', (select coalesce(json_agg(json_build_object('moneda', moneda, 'monto', monto,
                                                                     'ordenes', ordenes, 'proveedores', proveedores)
                                                   order by moneda <> 'PEN', moneda), '[]'::json)
                            from (select moneda, sum(subtotal) as monto, count(*) as ordenes,
                                         count(distinct supplier_id) as proveedores
                                    from ordenes group by moneda) x),
              'meses', (select coalesce(json_agg(to_char(m, 'YYYY-MM') order by m), '[]'::json)
                          from generate_series(date_trunc('month', cast(:desde as timestamptz) at time zone 'America/Lima'),
                                               date_trunc('month', now() at time zone 'America/Lima'),
                                               interval '1 month') as m),
              'porEmpresa', (select coalesce(json_agg(json_build_object('clave', empresa, 'detalle', null, 'moneda', moneda,
                                                                        'monto', monto, 'ordenes', ordenes)
                                                      order by moneda, monto desc), '[]'::json)
                               from (select empresa, moneda, sum(subtotal) as monto, count(*) as ordenes
                                       from ordenes group by empresa, moneda) x),
              'porMes', (select coalesce(json_agg(json_build_object('clave', mes, 'detalle', null, 'moneda', moneda,
                                                                    'monto', monto, 'ordenes', ordenes)
                                                  order by moneda, mes), '[]'::json)
                           from (select to_char(aprobada, 'YYYY-MM') as mes, moneda, sum(subtotal) as monto, count(*) as ordenes
                                   from ordenes group by 1, 2) x),
              'porCeco', (select coalesce(json_agg(json_build_object('clave', clave, 'detalle', detalle, 'moneda', moneda,
                                                                     'monto', round(monto, 2), 'ordenes', null)
                                                   order by moneda, n), '[]'::json)
                            from por_ceco where n <= 10),
              'porFamilia', (select coalesce(json_agg(json_build_object('clave', clave, 'detalle', null, 'moneda', moneda,
                                                                        'monto', round(monto, 2), 'ordenes', null)
                                                      order by moneda, n), '[]'::json)
                               from por_familia where n <= 10),
              'proveedores', (select coalesce(json_agg(json_build_object('clave', clave, 'detalle', null, 'moneda', moneda,
                                                                         'monto', monto, 'ordenes', ordenes)
                                                       order by moneda, n), '[]'::json)
                                from por_proveedor where n <= 8)
            )::text
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GastoService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public GastoDto obtener(int dias, String empresa, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = FiltrosDashboard.de(dias, empresa, null, principal);
        String json = jdbc.queryForObject(SQL, filtros.parametros(), String.class);
        return objectMapper.readValue(json, GastoDto.class);
    }
}
