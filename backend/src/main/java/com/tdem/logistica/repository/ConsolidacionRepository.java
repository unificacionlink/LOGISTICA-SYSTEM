package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Consolidacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsolidacionRepository extends JpaRepository<Consolidacion, UUID> {

    String DETALLE = "select c from Consolidacion c join fetch c.empresa join fetch c.producto " +
            "left join fetch c.variante join fetch c.uom left join fetch c.adjudicado a " +
            "left join fetch a.cotizacion q left join fetch q.proveedor left join fetch q.moneda ";

    @Query(DETALLE + "where c.status in :statuses order by c.createdAt")
    List<Consolidacion> findByStatusIn(@Param("statuses") Collection<String> statuses);

    @Query(DETALLE + "where c.consolidationId in :ids order by c.createdAt")
    List<Consolidacion> findDetalleByIds(@Param("ids") Collection<UUID> ids);

    @Query(DETALLE + "where c.consolidationId = :id")
    Optional<Consolidacion> findDetalleById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consolidacion c where c.consolidationId = :id")
    Optional<Consolidacion> bloquear(@Param("id") UUID id);

    /** Bloquea varios en una consulta, siempre en el mismo orden (id) para no cruzarse con otra operacion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consolidacion c where c.consolidationId in :ids order by c.consolidationId")
    List<Consolidacion> bloquearVarios(@Param("ids") java.util.Collection<UUID> ids);

    /** Consolidados adjudicados a lineas de la cotizacion y aun sin OC (emision por proveedor). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consolidacion c where c.status = 'ADJUDICADA' " +
            "and c.adjudicado.cotizacion.quotationId = :quotationId")
    List<Consolidacion> bloquearAdjudicadasDe(@Param("quotationId") UUID quotationId);
}
