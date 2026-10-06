package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Rfq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfqRepository extends JpaRepository<Rfq, UUID> {

    @Query("select r from Rfq r join fetch r.proveedor join fetch r.requerimiento " +
            "where r.requerimiento.requestId = :requestId order by r.createdAt desc")
    List<Rfq> findByRequestIdOrderByCreatedAtDesc(UUID requestId);

    @Query("select r from Rfq r join fetch r.proveedor join fetch r.requerimiento req " +
            "join fetch req.ceco c join fetch c.empresa where r.rfqId = :rfqId")
    Optional<Rfq> findDetalleById(UUID rfqId);

    @Query("select r from Rfq r join fetch r.proveedor join fetch r.requerimiento " +
            "where r.status in ('ENVIADA','RESPONDIDA') order by r.createdAt desc")
    List<Rfq> findAbiertas();
}
