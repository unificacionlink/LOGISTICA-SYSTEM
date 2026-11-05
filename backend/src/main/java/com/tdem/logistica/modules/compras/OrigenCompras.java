package com.tdem.logistica.modules.compras;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * De que requerimientos viene cada RFQ u OC (DEC-L039): por las necesidades de
 * sus consolidados o, en las previas al Lote 16, por su request_item_id. Una
 * sola consulta por lista, sin N+1.
 */
@Component
public class OrigenCompras {

    public record ReqRef(UUID requestId, String requestCode) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public OrigenCompras(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, List<ReqRef>> deOrdenes(Collection<UUID> orderIds) {
        return consultar("orden_item", "order_id", orderIds);
    }

    public Map<UUID, List<ReqRef>> deRfqs(Collection<UUID> rfqIds) {
        return consultar("rfq_item", "rfq_id", rfqIds);
    }

    public List<ReqRef> deOrden(UUID orderId) {
        return deOrdenes(List.of(orderId)).getOrDefault(orderId, List.of());
    }

    public List<ReqRef> deRfq(UUID rfqId) {
        return deRfqs(List.of(rfqId)).getOrDefault(rfqId, List.of());
    }

    /** requestId solo cuando hay un unico requerimiento (para enlazarlo). */
    public static UUID unico(List<ReqRef> refs) {
        return refs.size() == 1 ? refs.get(0).requestId() : null;
    }

    public static String codigos(List<ReqRef> refs) {
        return refs.isEmpty() ? null : refs.stream().map(ReqRef::requestCode).collect(Collectors.joining(", "));
    }

    private Map<UUID, List<ReqRef>> consultar(String tabla, String columna, Collection<UUID> ids) {
        Map<UUID, List<ReqRef>> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        // tabla/columna son constantes de esta clase, nunca entrada del usuario.
        String sql = """
                select distinct x.id, r.request_id, r.request_code from (
                  select t.%2$s as id, n.request_item_id
                    from %1$s t join necesidad_compra n on n.consolidation_id = t.consolidation_id
                   where t.%2$s in (:ids)
                  union
                  select t.%2$s, t.request_item_id from %1$s t
                   where t.%2$s in (:ids) and t.request_item_id is not null
                ) x
                join requerimiento_item ri on ri.request_item_id = x.request_item_id
                join requerimiento r on r.request_id = ri.request_id
                order by r.request_code""".formatted(tabla, columna);
        jdbc.query(sql, new MapSqlParameterSource("ids", ids), rs -> {
            out.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(new ReqRef(rs.getObject(2, UUID.class), rs.getString(3)));
        });
        return out;
    }
}
