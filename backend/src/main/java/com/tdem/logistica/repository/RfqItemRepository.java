package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RfqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RfqItemRepository extends JpaRepository<RfqItem, UUID> {

    @Query("select i from RfqItem i join fetch i.producto left join fetch i.variante join fetch i.uom " +
            "left join fetch i.consolidacion where i.rfq.rfqId = :rfqId")
    List<RfqItem> findByRfq(UUID rfqId);

    @Query("select i from RfqItem i join fetch i.rfq r join fetch r.proveedor " +
            "where i.consolidacion.consolidationId = :consolidationId order by r.createdAt")
    List<RfqItem> findByConsolidacion(@Param("consolidationId") UUID consolidationId);

    @Query("select i from RfqItem i join fetch i.rfq where i.consolidacion.consolidationId in :ids")
    List<RfqItem> findByConsolidaciones(@Param("ids") Collection<UUID> ids);
}
