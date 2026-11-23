package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RecepcionItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RecepcionItemRepository extends RepositorioBase<RecepcionItem, UUID> {

    /** Recibido por item de OC, en una sola consulta: filas [orderItemId, suma]. */
    @Query("select i.ordenItem.orderItemId, sum(i.qtyReceived) from RecepcionItem i " +
            "where i.ordenItem.orderItemId in :orderItemIds group by i.ordenItem.orderItemId")
    List<Object[]> sumarRecibidoPorItems(@Param("orderItemIds") Collection<UUID> orderItemIds);

    /** Recibido por item de OC en una consulta; los items sin recepciones no aparecen (= 0). */
    default Map<UUID, BigDecimal> recibidoPorItem(Collection<UUID> orderItemIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (orderItemIds.isEmpty()) {
            return out;
        }
        for (Object[] fila : sumarRecibidoPorItems(orderItemIds)) {
            out.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        return out;
    }

    @Query("select i from RecepcionItem i join fetch i.recepcion r join fetch i.ordenItem " +
            "where r.orden.orderId = :orderId")
    List<RecepcionItem> findByOrden(@Param("orderId") UUID orderId);

    /** Entrega directa (DEC-L084): recibido por destino de la OC, filas [orderItemId, needId, requestItemId, suma]. */
    @Query("select i.ordenItem.orderItemId, n.needId, q.requestItemId, sum(i.qtyReceived) from RecepcionItem i " +
            "join i.recepcion r left join i.necesidad n left join i.requerimientoItem q " +
            "where r.orden.orderId = :orderId and r.cecoDestino is not null " +
            "group by i.ordenItem.orderItemId, n.needId, q.requestItemId")
    List<Object[]> sumarDirectoPorDestino(@Param("orderId") UUID orderId);

    /** Entregado directo por el proveedor a cada item del requerimiento: filas [requestItemId, suma]. */
    @Query("select q.requestItemId, sum(i.qtyReceived) from RecepcionItem i join i.requerimientoItem q " +
            "where q.requerimiento.requestId = :requestId and i.recepcion.cecoDestino is not null " +
            "group by q.requestItemId")
    List<Object[]> sumarEntregadoDirectoPorItem(@Param("requestId") UUID requestId);

    /** OC de entrega directa con alguna linea para este requerimiento (DEC-L084). */
    @Query(value = """
            select distinct oi.order_id from orden_item oi
              join orden_abastecimiento o on o.order_id = oi.order_id and o.direct_delivery and o.status <> 'ANULADA'
              left join necesidad_compra n on n.consolidation_id = oi.consolidation_id and n.status = 'COMPRA'
              join requerimiento_item ri on ri.request_item_id = coalesce(oi.request_item_id, n.request_item_id)
             where ri.request_id = :requestId""", nativeQuery = true)
    List<UUID> ordenesDirectasDelRequerimiento(@Param("requestId") UUID requestId);

    /** Lineas entregadas directo a un requerimiento, con su recepcion, OC y CECO (Tracking, DEC-L084). */
    @Query("select i from RecepcionItem i join fetch i.recepcion r join fetch r.orden join fetch r.cecoDestino " +
            "join fetch r.receivedBy left join fetch r.documento join fetch i.ordenItem oi left join fetch oi.producto " +
            "left join fetch oi.variante join fetch i.requerimientoItem q " +
            "where q.requerimiento.requestId = :requestId order by r.receiptDate, r.receiptNumber")
    List<RecepcionItem> findDirectosPorRequerimiento(@Param("requestId") UUID requestId);
}
