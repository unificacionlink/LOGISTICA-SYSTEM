package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Aprobacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AprobacionRepository extends JpaRepository<Aprobacion, UUID> {

    @Query("select a from Aprobacion a where a.entityType = :entityType and a.entityId = :entityId " +
            "and a.status = 'PENDIENTE' order by a.createdAt desc")
    List<Aprobacion> findPendientesPorEntidad(@Param("entityType") String entityType, @Param("entityId") UUID entityId);

    default Optional<Aprobacion> findPendientePorEntidad(String entityType, UUID entityId) {
        List<Aprobacion> filas = findPendientesPorEntidad(entityType, entityId);
        return filas.isEmpty() ? Optional.empty() : Optional.of(filas.get(0));
    }

    @Query("select a from Aprobacion a where a.entityType = :entityType and a.status = 'PENDIENTE' " +
            "order by a.createdAt")
    List<Aprobacion> findPendientesPorTipo(@Param("entityType") String entityType);

    /** Tracking: todas las aprobaciones de la entidad (pendientes y decididas), en orden. */
    @Query("select a from Aprobacion a where a.entityType = :entityType and a.entityId = :entityId " +
            "order by a.createdAt")
    List<Aprobacion> findPorEntidad(@Param("entityType") String entityType, @Param("entityId") UUID entityId);
}
