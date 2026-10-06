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

    /**
     * REQ con algun item aun por comprar, en UNA consulta (antes eran 1 + 3 por
     * item). Misma formula que ComprasService.pendienteParaCompra: solicitado
     * - cubierto por stock - en RFQ activa - ya ordenado - comprado por Caja Chica.
     */
    @Query("select distinct i.requerimiento.requestId from RequerimientoItem i " +
            "where i.requerimiento.status in :statuses and i.qtyRequested " +
            "- coalesce((select sum(r.qtyReserved) from ReservaStock r where r.requerimientoItem = i " +
            "   and r.origin = 'VERIFICACION_STOCK' and r.status in ('RESERVADA','DESPACHADA')), 0) " +
            "- coalesce((select sum(q.qty) from RfqItem q where q.requerimientoItem = i and q.rfq.status <> 'CERRADA'), 0) " +
            "- coalesce((select sum(o.qtyOrdered) from OrdenItem o where o.requerimientoItem = i " +
            "   and o.orden.status <> 'ANULADA'), 0) " +
            "- coalesce((select sum(c.quantity) from CajaChicaItem c where c.requerimientoItem = i " +
            "   and c.ref.regularizationStatus <> 'ANULADO'), 0) > 0")
    List<UUID> findRequerimientosConFaltante(@Param("statuses") List<String> statuses);

    /** Cantidad del item comprada por Caja Chica (compras no anuladas). */
    @Query("select coalesce(sum(c.quantity), 0) from CajaChicaItem c " +
            "where c.requerimientoItem.requestItemId = :requestItemId and c.ref.regularizationStatus <> 'ANULADO'")
    java.math.BigDecimal sumarCompradoPorCajaChica(@Param("requestItemId") UUID requestItemId);
}
