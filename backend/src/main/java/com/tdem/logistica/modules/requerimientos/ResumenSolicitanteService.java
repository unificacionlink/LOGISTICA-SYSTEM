package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.modules.requerimientos.dto.ResumenSolicitanteDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * "Mis requerimientos" del Inicio (DEC-L076): conteo por estado, unidades
 * pedidas y entregadas, lo que mas pide y sus ultimos requerimientos con su
 * avance. Siempre de quien consulta (sin parametro de usuario) y en UNA
 * consulta que devuelve JSON, como el dashboard.
 */
@Service
public class ResumenSolicitanteService {

    private static final String SQL = """
            with mis as (
              select r.request_id, r.request_code, r.status, r.created_at
                from requerimiento r where r.requester_user_id = :yo
            ),
            it as (
              select ri.request_id, ri.request_item_id, ri.product_id, ri.qty_requested as q, m.status
                from requerimiento_item ri join mis m on m.request_id = ri.request_id
            ),
            ent as (
              select it.request_item_id,
                     coalesce((select sum(si.dest_accepted_qty) from salida_item si
                                where si.request_item_id = it.request_item_id), 0)
                   + coalesce((select sum(rci.qty_received) from recepcion_item rci
                                where rci.request_item_id = it.request_item_id), 0)
                   + coalesce((select sum(cci.quantity) from caja_chica_item cci
                                 join caja_chica_ref cr on cr.petty_cash_ref_id = cci.petty_cash_ref_id
                                where cci.request_item_id = it.request_item_id and cr.destination = 'PROYECTO'
                                  and cr.regularization_status = 'REGULARIZADO'), 0) as entregado
                from it
            ),
            pi as (
              select it.*, least(coalesce(e.entregado, 0), it.q) as entregado
                from it left join ent e on e.request_item_id = it.request_item_id
            ),
            enviados as (select * from pi where status not in ('BORRADOR', 'RECHAZADO'))
            select json_build_object(
              'total', (select count(*) from mis),
              'estados', coalesce((select json_object_agg(status, n)
                                     from (select status, count(*) n from mis group by status) x), '{}'::json),
              'unidadesPedidas', (select coalesce(sum(q), 0) from enviados),
              'unidadesEntregadas', (select coalesce(sum(entregado), 0) from enviados),
              'productos', coalesce((select json_agg(x) from (
                  select p.sku_base as sku, p.name as nombre, count(distinct e.request_id) as veces,
                         sum(e.q) as pedidas, sum(e.entregado) as entregadas
                    from enviados e join producto p on p.product_id = e.product_id
                   group by p.sku_base, p.name
                   order by sum(e.q) desc, p.name limit 5) x), '[]'::json),
              'recientes', coalesce((select json_agg(x) from (
                  select m.request_id as id, m.request_code as codigo, m.status as estado, m.created_at as creado,
                         (select count(*) from pi where pi.request_id = m.request_id) as items,
                         (select string_agg(p.name, ' · ' order by p.name) from pi
                            join producto p on p.product_id = pi.product_id where pi.request_id = m.request_id) as resumen,
                         coalesce((select avg(pi.entregado / nullif(pi.q, 0)) from pi
                                    where pi.request_id = m.request_id), 0) as avance
                    from mis m order by m.created_at desc limit 6) x), '[]'::json)
            )""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ResumenSolicitanteService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Sin transaccion: es una sola lectura (sin la ida y vuelta del COMMIT). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResumenSolicitanteDto resumen(UsuarioPrincipal principal) {
        String json = jdbc.queryForObject(SQL, new MapSqlParameterSource("yo", principal.userId()), String.class);
        return objectMapper.readValue(json, ResumenSolicitanteDto.class);
    }
}
