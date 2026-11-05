package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Cotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CotizacionRepository extends JpaRepository<Cotizacion, UUID> {

    @Query("select c from Cotizacion c join fetch c.proveedor join fetch c.moneda " +
            "where c.rfq.rfqId = :rfqId order by c.total")
    List<Cotizacion> findByRfq(UUID rfqId);

    @Query("select c from Cotizacion c join fetch c.rfq r join fetch r.empresa " +
            "join fetch c.proveedor join fetch c.moneda where c.quotationId = :quotationId")
    Optional<Cotizacion> findDetalleById(UUID quotationId);
}
