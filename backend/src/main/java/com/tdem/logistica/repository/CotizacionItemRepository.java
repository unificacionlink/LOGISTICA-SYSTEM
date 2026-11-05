package com.tdem.logistica.repository;

import com.tdem.logistica.entity.CotizacionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CotizacionItemRepository extends JpaRepository<CotizacionItem, UUID> {

    @Query("select i from CotizacionItem i join fetch i.producto left join fetch i.variante " +
            "join fetch i.rfqItem where i.cotizacion.quotationId = :quotationId")
    List<CotizacionItem> findByCotizacion(UUID quotationId);

    /** Comparativo de un consolidado: lo cotizado por cada proveedor a sus lineas de RFQ. */
    @Query("select i from CotizacionItem i join fetch i.cotizacion c join fetch c.proveedor join fetch c.moneda " +
            "join fetch i.rfqItem ri left join fetch c.documento where ri.rfqItemId in :rfqItemIds")
    List<CotizacionItem> findByRfqItems(@Param("rfqItemIds") Collection<UUID> rfqItemIds);
}
