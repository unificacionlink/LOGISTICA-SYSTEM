package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Dashboard del Inicio (DEC-L020): avance del proceso, respuesta de
 * proveedores y ahorro por elegir la mejor cotizacion. Todo en UNA consulta
 * que devuelve JSON: con la BD lejos, cada ida y vuelta cuesta ~150 ms.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Pattern CODIGO_EMPRESA = Pattern.compile("[A-Z0-9]{2,20}");

    /*
     * reqs    = requerimientos de la empresa filtrada (todas si :empresa es null).
     * entrega = cuando se completo cada REQ (ultimo envio recibido en destino).
     * rfqs    = RFQ del periodo con su primera respuesta y si alguna se eligio.
     * comp    = por REQ con cotizacion elegida: cuantas hubo en la misma moneda y la mayor.
     */
    private static final String SQL = """
            with reqs as (
              select r.request_id, r.request_code, r.status, r.created_at
                from requerimiento r join empresa e on e.company_id = r.company_id
               where cast(:empresa as text) is null or e.code = cast(:empresa as text)
            ),
            entrega as (
              select v.request_id, max(v.received_at) as entregado_en
                from envio v
               where v.status in ('RECEPCIONADO','CERRADO') and v.request_id is not null
               group by v.request_id
            ),
            aprob as (
              select a.created_at, a.decided_at
                from aprobacion a join reqs on reqs.request_id = a.entity_id
               where a.entity_type = 'REQUERIMIENTO' and a.status = 'APROBADO' and a.decided_at >= :desde
            ),
            rfqs as (
              select q.rfq_id, q.supplier_id, q.created_at,
                     (select min(c.received_at) from cotizacion c where c.rfq_id = q.rfq_id) as respondida_en,
                     exists (select 1 from cotizacion c where c.rfq_id = q.rfq_id and c.status = 'SELECCIONADA') as elegida
                from rfq q join reqs on reqs.request_id = q.request_id
               where q.status <> 'BORRADOR' and q.created_at >= :desde
            ),
            cot as (
              select q.request_id, c.quotation_id, c.total, c.currency_code, c.status, c.received_at,
                     p.legal_name
                from cotizacion c
                join rfq q on q.rfq_id = c.rfq_id
                join proveedor p on p.supplier_id = c.supplier_id
                join reqs on reqs.request_id = q.request_id
               where c.status <> 'DESCARTADA'
            ),
            comp as (
              select sel.request_id, sel.currency_code as moneda, sel.total as elegido,
                     sel.legal_name as proveedor, sel.received_at,
                     count(o.quotation_id) as n, max(o.total) as mayor
                from cot sel
                join cot o on o.request_id = sel.request_id and o.currency_code = sel.currency_code
               where sel.status = 'SELECCIONADA' and sel.received_at >= :desde
               group by sel.request_id, sel.currency_code, sel.total, sel.legal_name, sel.received_at
            )
            select json_build_object(
              'abiertos', (select count(*) from reqs
                            where status in ('PENDIENTE_APROBACION','OBSERVADO','VERIFICACION_STOCK','EN_ATENCION','ATENCION_PARCIAL')),
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
                  (select count(distinct q.request_id) from rfq q join reqs using (request_id)
                    where q.status in ('ENVIADA','RESPONDIDA'))),
                json_build_object('codigo', 'OC_EN_CURSO', 'cantidad',
                  (select count(distinct ri.request_id)
                     from orden_item oi
                     join orden_abastecimiento o on o.order_id = oi.order_id
                     join requerimiento_item ri on ri.request_item_id = oi.request_item_id
                     join reqs on reqs.request_id = ri.request_id
                    where o.status in ('EMITIDA','EN_ATENCION','RECEPCION_PARCIAL'))),
                json_build_object('codigo', 'POR_DESPACHAR', 'cantidad',
                  (select count(distinct ri.request_id)
                     from reserva_stock rs
                     join requerimiento_item ri on ri.request_item_id = rs.request_item_id
                     join reqs on reqs.request_id = ri.request_id
                    where rs.status = 'RESERVADA')),
                json_build_object('codigo', 'EN_TRANSITO', 'cantidad',
                  (select count(distinct v.request_id) from envio v join reqs using (request_id)
                    where v.status in ('PENDIENTE','EN_TRANSITO')))
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
                                 from (select r.request_code as "codigo", comp.proveedor as "proveedor",
                                              comp.n as "cotizaciones", comp.moneda as "moneda",
                                              comp.elegido as "elegido", comp.mayor as "mayor",
                                              comp.mayor - comp.elegido as "ahorro", comp.received_at as "fecha"
                                         from comp join reqs r using (request_id)
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
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN, Roles.APROBADOR)) {
            throw ApiException.forbidden("No tienes permiso para ver el dashboard.");
        }
        if (dias < 7 || dias > 730) {
            throw ApiException.badRequest("El periodo debe estar entre 7 y 730 dias.");
        }
        String codigo = empresa == null || empresa.isBlank() ? null : empresa.trim().toUpperCase();
        if (codigo != null && !CODIGO_EMPRESA.matcher(codigo).matches()) {
            throw ApiException.badRequest("Empresa invalida.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("desde", OffsetDateTime.now().minusDays(dias))
                .addValue("empresa", codigo, java.sql.Types.VARCHAR);
        String json = jdbc.queryForObject(SQL, params, String.class);
        return objectMapper.readValue(json, DashboardDto.class);
    }
}
