package com.tdem.logistica.repository;

import com.tdem.logistica.entity.NecesidadCompra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NecesidadCompraRepository extends JpaRepository<NecesidadCompra, UUID> {

    String DETALLE = "select n from NecesidadCompra n join fetch n.requerimientoItem ri join fetch ri.requerimiento r " +
            "join fetch r.ceco join fetch r.empresa join fetch n.producto left join fetch n.variante join fetch n.uom " +
            "left join fetch n.consolidacion ";

    @Query(DETALLE + "where n.status in :statuses order by r.createdAt, n.createdAt")
    List<NecesidadCompra> findByStatusIn(@Param("statuses") Collection<String> statuses);

    /** Orden de reparto de lo recibido: critica, urgente, normal; luego la mas antigua. */
    @Query(DETALLE + "where n.consolidacion.consolidationId in :ids and n.status <> 'ANULADA' " +
            "order by case r.priority when 'CRITICA' then 0 when 'URGENTE' then 1 else 2 end, r.createdAt, n.createdAt")
    List<NecesidadCompra> findByConsolidaciones(@Param("ids") Collection<UUID> ids);

    @Query(DETALLE + "where r.requestId = :requestId order by n.createdAt")
    List<NecesidadCompra> findByRequerimiento(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NecesidadCompra n where n.needId = :id")
    Optional<NecesidadCompra> bloquear(@Param("id") UUID id);
}
