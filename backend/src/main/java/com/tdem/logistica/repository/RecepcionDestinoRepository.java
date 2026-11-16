package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RecepcionDestino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecepcionDestinoRepository extends JpaRepository<RecepcionDestino, UUID> {

    boolean existsByClientToken(UUID clientToken);

    @Query("select coalesce(max(r.receiptNumber), 0) from RecepcionDestino r where r.envio.shipmentId = :shipmentId")
    Integer maxNumeroPorEnvio(@Param("shipmentId") UUID shipmentId);

    @Query("select r from RecepcionDestino r join fetch r.receivedBy left join fetch r.documento " +
            "where r.envio.shipmentId = :shipmentId order by r.receiptNumber")
    List<RecepcionDestino> findByEnvio(@Param("shipmentId") UUID shipmentId);
}
