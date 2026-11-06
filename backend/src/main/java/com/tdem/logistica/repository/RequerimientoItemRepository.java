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

    /** Resumen del listado (cantidad de items y productos), en una sola lectura para todos los REQ. */
    @Query(value = """
            select cast(ri.request_id as varchar), count(*), left(string_agg(p.name, ' · ' order by p.name), 400)
            from requerimiento_item ri join producto p on p.product_id = ri.product_id
            group by ri.request_id""", nativeQuery = true)
    List<Object[]> resumenTodos();

    @Query(value = """
            select cast(ri.request_id as varchar), count(*), left(string_agg(p.name, ' · ' order by p.name), 400)
            from requerimiento_item ri join producto p on p.product_id = ri.product_id
            join requerimiento r on r.request_id = ri.request_id
            where r.requester_user_id = :userId
            group by ri.request_id""", nativeQuery = true)
    List<Object[]> resumenDeSolicitante(@Param("userId") UUID userId);

    /** Cantidad del item comprada por Caja Chica (compras no anuladas). */
    @Query("select coalesce(sum(c.quantity), 0) from CajaChicaItem c " +
            "where c.requerimientoItem.requestItemId = :requestItemId and c.ref.regularizationStatus <> 'ANULADO'")
    java.math.BigDecimal sumarCompradoPorCajaChica(@Param("requestItemId") UUID requestItemId);
}
