package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RecepcionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecepcionItemRepository extends JpaRepository<RecepcionItem, UUID> {

    @Query("select coalesce(sum(i.qtyReceived), 0) from RecepcionItem i where i.ordenItem.orderItemId = :orderItemId")
    BigDecimal sumarRecibido(@Param("orderItemId") UUID orderItemId);

    /** Recibido por item de OC, en una sola consulta: filas [orderItemId, suma]. */
    @Query("select i.ordenItem.orderItemId, sum(i.qtyReceived) from RecepcionItem i " +
            "where i.ordenItem.orderItemId in :orderItemIds group by i.ordenItem.orderItemId")
    List<Object[]> sumarRecibidoPorItems(@Param("orderItemIds") Collection<UUID> orderItemIds);

    @Query("select i from RecepcionItem i join fetch i.recepcion r join fetch i.ordenItem " +
            "where r.orden.orderId = :orderId")
    List<RecepcionItem> findByOrden(@Param("orderId") UUID orderId);
}
