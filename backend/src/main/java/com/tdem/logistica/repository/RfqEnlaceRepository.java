package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RfqEnlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RfqEnlaceRepository extends JpaRepository<RfqEnlace, UUID> {

    @Query("select e from RfqEnlace e join fetch e.rfq r join fetch r.empresa join fetch r.proveedor " +
            "where e.tokenHash = :tokenHash")
    Optional<RfqEnlace> findByTokenHash(String tokenHash);

    @Query("select e from RfqEnlace e where e.rfq.rfqId = :rfqId and e.revokedAt is null")
    Optional<RfqEnlace> findVigenteDeRfq(UUID rfqId);
}
