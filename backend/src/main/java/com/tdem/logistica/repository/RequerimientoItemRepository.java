package com.tdem.logistica.repository;

import com.tdem.logistica.entity.RequerimientoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RequerimientoItemRepository extends JpaRepository<RequerimientoItem, UUID> {

    @Query("select i from RequerimientoItem i " +
            "join fetch i.producto join fetch i.uom left join fetch i.variante " +
            "where i.requerimiento.requestId = :requestId")
    List<RequerimientoItem> findByRequerimiento(UUID requestId);

    /** Cantidad del item comprada por Caja Chica (compras no anuladas). */
    @Query("select coalesce(sum(c.quantity), 0) from CajaChicaItem c " +
            "where c.requerimientoItem.requestItemId = :requestItemId and c.ref.regularizationStatus <> 'ANULADO'")
    java.math.BigDecimal sumarCompradoPorCajaChica(@Param("requestItemId") UUID requestItemId);
}
