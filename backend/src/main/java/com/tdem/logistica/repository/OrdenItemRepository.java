package com.tdem.logistica.repository;

import com.tdem.logistica.entity.OrdenItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrdenItemRepository extends JpaRepository<OrdenItem, UUID> {

    @Query("select coalesce(sum(i.qtyOrdered), 0) from OrdenItem i " +
            "where i.requerimientoItem.requestItemId = :requestItemId and i.orden.status <> 'ANULADA'")
    BigDecimal sumarQtyOrdenada(@Param("requestItemId") UUID requestItemId);

    @Query("select i from OrdenItem i left join fetch i.producto left join fetch i.variante " +
            "left join fetch i.uom where i.orden.orderId = :orderId")
    List<OrdenItem> findByOrden(UUID orderId);

    /** RF-031: items de todas las OC que aun admiten recepcion (plantilla y validacion de la carga masiva). */
    @Query("select i from OrdenItem i join fetch i.orden o join fetch o.proveedor join fetch o.empresa " +
            "left join fetch i.producto left join fetch i.variante left join fetch i.uom " +
            "where o.status in :statuses order by o.createdAt, i.orderItemId")
    List<OrdenItem> findPorRecibir(@Param("statuses") List<String> statuses);
}
