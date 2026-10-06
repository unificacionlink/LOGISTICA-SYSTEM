package com.tdem.logistica.repository;

import com.tdem.logistica.entity.IncidenciaRecepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IncidenciaRecepcionRepository extends JpaRepository<IncidenciaRecepcion, UUID> {

    @Query("select i from IncidenciaRecepcion i join fetch i.recepcion r join fetch i.ordenItem " +
            "where r.orden.orderId = :orderId order by i.createdAt")
    List<IncidenciaRecepcion> findByOrden(@Param("orderId") UUID orderId);
}
