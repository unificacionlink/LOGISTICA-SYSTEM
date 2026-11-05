package com.tdem.logistica.repository;

import com.tdem.logistica.entity.OrdenAbastecimiento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrdenAbastecimientoRepository extends JpaRepository<OrdenAbastecimiento, UUID> {

    @Query("select o from OrdenAbastecimiento o join fetch o.proveedor join fetch o.moneda " +
            "join fetch o.cotizacion c join fetch o.empresa " +
            "order by o.createdAt desc")
    List<OrdenAbastecimiento> findAllConDetalle();

    @Query("select o from OrdenAbastecimiento o join fetch o.proveedor join fetch o.moneda " +
            "join fetch o.cotizacion c join fetch o.empresa " +
            "where o.orderId = :orderId")
    Optional<OrdenAbastecimiento> findDetalleById(UUID orderId);

    @Query("select o from OrdenAbastecimiento o join fetch o.proveedor join fetch o.moneda " +
            "join fetch o.cotizacion c join fetch o.empresa " +
            "where o.status in :statuses order by o.createdAt")
    List<OrdenAbastecimiento> findByStatusInConDetalle(@Param("statuses") List<String> statuses);

    /** Serializa recepciones concurrentes de la misma OC (numeracion y pendiente). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenAbastecimiento o where o.orderId = :orderId")
    Optional<OrdenAbastecimiento> bloquear(@Param("orderId") UUID orderId);

    /** Tracking: OC que compran algun item del requerimiento. */
    @Query("select o from OrdenAbastecimiento o join fetch o.proveedor where exists (" +
            "select 1 from OrdenItem i where i.orden = o and i.requerimientoItem.requerimiento.requestId = :requestId) " +
            "or exists (select 1 from OrdenItem i, NecesidadCompra n where i.orden = o " +
            "  and n.consolidacion = i.consolidacion and n.requerimientoItem.requerimiento.requestId = :requestId) " +
            "order by o.createdAt")
    List<OrdenAbastecimiento> findByRequerimiento(@Param("requestId") UUID requestId);
}
