package com.tdem.logistica.repository;

import com.tdem.logistica.entity.CotizacionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CotizacionItemRepository extends JpaRepository<CotizacionItem, UUID> {

    @Query("select i from CotizacionItem i join fetch i.producto left join fetch i.variante " +
            "join fetch i.rfqItem where i.cotizacion.quotationId = :quotationId")
    List<CotizacionItem> findByCotizacion(UUID quotationId);
}
