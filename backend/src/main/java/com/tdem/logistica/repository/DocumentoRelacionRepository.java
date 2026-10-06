package com.tdem.logistica.repository;

import com.tdem.logistica.entity.DocumentoRelacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentoRelacionRepository extends JpaRepository<DocumentoRelacion, UUID> {

    @Query("select r from DocumentoRelacion r join fetch r.documento d " +
            "where r.entityType = :entityType and r.entityId = :entityId and d.status = 'ACTIVO' " +
            "order by d.uploadedAt")
    List<DocumentoRelacion> findDocumentos(@Param("entityType") String entityType, @Param("entityId") UUID entityId);

    /** Igual que findDocumentos, para varias entidades en una sola consulta. */
    @Query("select r from DocumentoRelacion r join fetch r.documento d " +
            "where r.entityType = :entityType and r.entityId in :entityIds and d.status = 'ACTIVO' " +
            "order by d.uploadedAt")
    List<DocumentoRelacion> findDocumentosDe(@Param("entityType") String entityType,
                                             @Param("entityIds") Collection<UUID> entityIds);
}
