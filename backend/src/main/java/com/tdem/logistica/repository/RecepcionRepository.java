package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Recepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecepcionRepository extends JpaRepository<Recepcion, UUID> {

    @Query("select coalesce(max(r.receiptNumber), 0) from Recepcion r where r.orden.orderId = :orderId")
    Integer maxNumeroPorOrden(@Param("orderId") UUID orderId);

    @Query("select r from Recepcion r join fetch r.receivedBy where r.orden.orderId = :orderId order by r.receiptNumber")
    List<Recepcion> findByOrden(@Param("orderId") UUID orderId);

    boolean existsByOrden_OrderId(UUID orderId);

    @Query("select r from Recepcion r join fetch r.orden join fetch r.receivedBy " +
            "where r.orden.orderId in :orderIds order by r.receiptDate")
    List<Recepcion> findByOrdenes(@Param("orderIds") List<UUID> orderIds);
}
