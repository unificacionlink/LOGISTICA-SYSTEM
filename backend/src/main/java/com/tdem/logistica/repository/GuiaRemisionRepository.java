package com.tdem.logistica.repository;

import com.tdem.logistica.entity.GuiaRemision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GuiaRemisionRepository extends JpaRepository<GuiaRemision, UUID> {

    boolean existsByIssuerRucAndSeriesAndNumber(String issuerRuc, String series, String number);

    @Query("select g from GuiaRemision g where g.orden.orderId = :orderId")
    List<GuiaRemision> findByOrden(@Param("orderId") UUID orderId);
}
