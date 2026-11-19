package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RecepcionDestinoItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecepcionDestinoItemRepository extends RepositorioBase<RecepcionDestinoItem, UUID> {

    @Query("select i from RecepcionDestinoItem i join fetch i.salidaItem si join fetch si.producto " +
            "left join fetch si.variante where i.recepcion.envio.shipmentId = :shipmentId")
    List<RecepcionDestinoItem> findByEnvio(@Param("shipmentId") UUID shipmentId);
}
