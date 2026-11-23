package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Dashboard del Inicio (DEC-L020): avance del proceso, respuesta de
 * proveedores y ahorro por elegir la mejor cotizacion. Todo en UNA consulta
 * que devuelve JSON: con la BD lejos, cada ida y vuelta cuesta ~150 ms.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /*
     * reqs    = requerimientos de la empresa filtrada (todas si :empresa es null).
     * entrega = cuando se completo cada REQ: ultimo envio recibido en destino, caja chica del proyecto
     *           regularizada o entrega directa del proveedor (lo mismo que cuenta AtencionCalculator).
     * plazo   = REQ completados en el periodo: si llegaron a su fecha requerida (SlaAtencion, DEC-L068)
     *           y si necesitaron compra o salieron solo del stock (DEC-L083).
     * rfqs    = RFQ enviadas del periodo (por empresa) con su primera respuesta y si alguna se eligio.
     * comp    = por consolidado adjudicado: cuantas ofertas hubo en la misma moneda y la mas cara
     *           (a la cantidad adjudicada), DEC-L039.
     */
    private static final String SQL = """
            with reqs as (
              select r.request_id, r.request_code, r.status, r.created_at, r.required_date
                from requerimiento r join empresa e on e.company_id = r.company_id
               where cast(:empresa as text) is null or e.code = cast(:empresa as text)
            ),
            entrega as (
              select request_id, max(en) as entregado_en
                from (select v.request_id, v.received_at as en
                        from envio v
                       where v.status in ('RECEPCION_PARCIAL','RECEPCIONADO','RECEPCIONADO_OBSERVADO','CERRADO')
                         and v.request_id is not null
                      union all
                      select f.request_id, f.regularized_at
                        from caja_chica_ref f
                       where f.destination = 'PROYECTO' and f.regularization_status = 'REGULARIZADO'
                         and f.request_id is not null
                      union all
                      select ri.request_id, rc.receipt_date
                        from recepcion rc
                        join recepcion_item rci on rci.receipt_id = rc.receipt_id
                        join requerimiento_item ri on ri.request_item_id = rci.request_item_id
                       where rc.destination_ceco_id is not null) e
               group by request_id
            ),
            plazo as (
              select (entrega.entregado_en at time zone 'America/Lima')::date <= reqs.required_date as a_tiempo,
                     exists (select 1 from necesidad_compra n
                               join requerimiento_item ri on ri.request_item_id = n.request_item_id
                              where ri.request_id = reqs.request_id and n.status <> 'ANULADA') as con_compra
                from reqs join entrega using (request_id)
               where reqs.status = 'ATENCION_COMPLETA' and entrega.entregado_en >= :desde
                 and reqs.required_date is not null
            ),
            aprob as (
              select a.created_at, a.decided_at
                from aprobacion a join reqs on reqs.request_id = a.entity_id
               where a.entity_type = 'REQUERIMIENTO' and a.status = 'APROBADO' and a.decided_at >= :desde
            ),
            emp as (
              select company_id from empresa
               where cast(:empresa as text) is null or code = cast(:empresa as text)
            ),
            rfqs as (
              select q.rfq_id, q.supplier_id, coalesce(q.sent_at, q.created_at) as created_at,
                     (select min(c.received_at) from cotizacion c where c.rfq_id = q.rfq_id) as respondida_en,
                     exists (select 1 from cotizacion c where c.rfq_id = q.rfq_id and c.status = 'SELECCIONADA') as elegida
                from rfq q join emp using (company_id)
               where q.status <> 'BORRADOR' and q.created_at >= :desde
            ),
            ofertas as (
              select ri.consolidation_id, ci.quotation_item_id, ci.unit_cost, c.currency_code
                from cotizacion_item ci
                join cotizacion c on c.quotation_id = ci.quotation_id
                join rfq_item ri on ri.rfq_item_id = ci.rfq_item_id
               where c.status <> 'DESCARTADA' and ri.consolidation_id is not null
            ),
            comp as (
              select k.consolidation_code as codigo, c.currency_code as moneda, sel.line_total as elegido,
                     p.legal_name as proveedor, coalesce(k.awarded_at, c.received_at) as received_at,
                     count(o.quotation_item_id) as n, round(max(o.unit_cost) * sel.qty, 2) as mayor
                from consolidacion k
                join emp using (company_id)
                join cotizacion_item sel on sel.quotation_item_id = k.awarded_quotation_item_id
                join cotizacion c on c.quotation_id = sel.quotation_id
                join proveedor p on p.supplier_id = c.supplier_id
                join ofertas o on o.consolidation_id = k.consolidation_id and o.currency_code = c.currency_code
               where k.status in ('ADJUDICADA','ORDENADA') and coalesce(k.awarded_at, c.received_at) >= :desde
               group by k.consolidation_code, c.currency_code, sel.line_total, sel.qty, p.legal_name,
                        k.awarded_at, c.received_at
            )
            select json_build_object(
              'abiertos', (select count(*) from reqs
                            where status in ('PENDIENTE_APROBACION','OBSERVADO','VERIFICACION_STOCK','EN_ATENCION','ATENCION_PARCIAL')),
              'vencidos', (select count(*) from reqs
                            where status in ('PENDIENTE_APROBACION','OBSERVADO','VERIFICACION_STOCK','EN_ATENCION','ATENCION_PARCIAL')
                              and required_date < (now() at time zone 'America/Lima')::date),
              'cumplimiento', (select json_build_object(
                                 'entregados', count(*),
                                 'aTiempo', count(*) filter (where a_tiempo),
                                 'desdeStock', count(*) filter (where not con_compra),
                                 'desdeStockATiempo', count(*) filter (where not con_compra and a_tiempo),
                                 'conCompra', count(*) filter (where con_compra),
                                 'conCompraATiempo', count(*) filter (where con_compra and a_tiempo))
                                 from plazo),
              'creados', (select count(*) from reqs where created_at >= :desde and status <> 'BORRADOR'),
              'completados', (select count(*) from reqs join entrega using (request_id)
                               where reqs.status = 'ATENCION_COMPLETA' and entrega.entregado_en >= :desde),
              'horasAprobacion', (select round((avg(extract(epoch from decided_at - created_at)) / 3600)::numeric, 1)
                                    from aprob),
              'diasCiclo', (select round((avg(extract(epoch from entrega.entregado_en - reqs.created_at)) / 86400)::numeric, 1)
                              from reqs join entrega using (request_id)
                             where reqs.status = 'ATENCION_COMPLETA' and entrega.entregado_en >= :desde),
              'rfqEnviadas', (select count(*) from rfqs),
              'rfqRespondidas', (select count(*) from rfqs where respondida_en is not null),
              'diasRespuesta', (select round((avg(extract(epoch from respondida_en - created_at)) / 86400)::numeric, 1)
                                  from rfqs where respondida_en is not null),
              'ahorro', (select coalesce(json_agg(json_build_object('moneda', moneda, 'monto', monto, 'compras', compras)
                                                  order by moneda), '[]'::json)
                           from (select moneda, sum(mayor - elegido) as monto, count(*) as compras
                                   from comp where n >= 2 group by moneda) x),
              'comprasConComparativo', (select count(*) from comp where n >= 2),
              'comprasTotal', (select count(*) from comp),
              'etapas', json_build_array(
                json_build_object('codigo', 'POR_APROBAR', 'cantidad',
                  (select count(*) from reqs where status = 'PENDIENTE_APROBACION')),
                json_build_object('codigo', 'EN_COTIZACION', 'cantidad',
                  (select count(distinct ri.request_id)
                     from necesidad_compra n
                     join consolidacion k on k.consolidation_id = n.consolidation_id
                     join requerimiento_item ri on ri.request_item_id = n.request_item_id
                     join reqs on reqs.request_id = ri.request_id
                    where n.status = 'COMPRA' and k.status in ('ABIERTA','EN_COTIZACION','ADJUDICADA'))),
                json_build_object('codigo', 'OC_EN_CURSO', 'cantidad',
                  (select count(distinct ri.request_id)
                     from orden_item oi
                     join orden_abastecimiento o on o.order_id = oi.order_id
                     join necesidad_compra n on n.consolidation_id = oi.consolidation_id and n.status <> 'ANULADA'
                     join requerimiento_item ri on ri.request_item_id = n.request_item_id
                     join reqs on reqs.request_id = ri.request_id
                    where o.status in ('POR_APROBAR','EMITIDA','EN_ATENCION','RECEPCION_PARCIAL'))),
                json_build_object('codigo', 'POR_DESPACHAR', 'cantidad',
                  (select count(distinct ri.request_id)
                     from reserva_stock rs
                     join requerimiento_item ri on ri.request_item_id = rs.request_item_id
                     join reqs on reqs.request_id = ri.request_id
                    where rs.status = 'RESERVADA')),
                json_build_object('codigo', 'EN_TRANSITO', 'cantidad',
                  (select count(distinct v.request_id) from envio v join reqs using (request_id)
                    where v.status in ('PENDIENTE','EN_TRANSITO','RECEPCION_PARCIAL')))
              ),
              'semanas', (select coalesce(json_agg(json_build_object('semana', to_char(s.semana, 'YYYY-MM-DD'),
                                                                     'creados', coalesce(c.n, 0))
                                                   order by s.semana), '[]'::json)
                            from generate_series(date_trunc('week', cast(:desde as timestamptz)),
                                                 date_trunc('week', now()), interval '1 week') as s(semana)
                            left join (select date_trunc('week', created_at) as sem, count(*) as n
                                         from reqs where created_at >= :desde and status <> 'BORRADOR'
                                        group by 1) c on c.sem = s.semana),
              'proveedores', (select coalesce(json_agg(x), '[]'::json)
                                from (select p.legal_name as "proveedor",
                                             count(*) as "enviadas",
                                             count(respondida_en) as "respondidas",
                                             round((avg(extract(epoch from respondida_en - rfqs.created_at)) / 86400)::numeric, 1)
                                               as "diasRespuesta",
                                             count(*) filter (where elegida) as "elegidas"
                                        from rfqs join proveedor p on p.supplier_id = rfqs.supplier_id
                                       group by p.legal_name
                                       order by count(*) desc, p.legal_name
                                       limit 8) x),
              'comparativos', (select coalesce(json_agg(x), '[]'::json)
                                 from (select comp.codigo as "codigo", comp.proveedor as "proveedor",
                                              comp.n as "cotizaciones", comp.moneda as "moneda",
                                              comp.elegido as "elegido", comp.mayor as "mayor",
                                              comp.mayor - comp.elegido as "ahorro", comp.received_at as "fecha"
                                         from comp
                                        where comp.n >= 2
                                        order by comp.received_at desc
                                        limit 6) x)
            )::text
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DashboardService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DashboardDto obtener(int dias, String empresa, UsuarioPrincipal principal) {
        FiltrosDashboard filtros = FiltrosDashboard.de(dias, empresa, null, principal);
        String json = jdbc.queryForObject(SQL, filtros.parametros(), String.class);
        return objectMapper.readValue(json, DashboardDto.class);
    }
}
