package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RfqItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RfqItemRepository extends JpaRepository<RfqItem, UUID> {

    @Query("select coalesce(sum(i.qty), 0) from RfqItem i " +
            "where i.requerimientoItem.requestItemId = :requestItemId and i.rfq.status <> 'CERRADA'")
    BigDecimal sumarQtyEnRfqActivo(@Param("requestItemId") UUID requestItemId);

    @Query("select i from RfqItem i join fetch i.producto left join fetch i.variante join fetch i.uom " +
            "where i.rfq.rfqId = :rfqId")
    List<RfqItem> findByRfq(UUID rfqId);
}
