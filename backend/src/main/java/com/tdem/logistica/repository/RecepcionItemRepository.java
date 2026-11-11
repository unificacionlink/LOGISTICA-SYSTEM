package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RecepcionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RecepcionItemRepository extends JpaRepository<RecepcionItem, UUID> {

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
}
