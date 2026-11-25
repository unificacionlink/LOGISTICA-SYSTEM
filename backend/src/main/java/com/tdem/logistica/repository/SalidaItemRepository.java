package com.tdem.logistica.repository;

import com.tdem.logistica.entity.SalidaItem;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalidaItemRepository extends RepositorioBase<SalidaItem, UUID> {

    @Query("select i from SalidaItem i join fetch i.producto left join fetch i.variante " +
            "where i.salida.issueId = :issueId")
    List<SalidaItem> findBySalida(@Param("issueId") UUID issueId);

    /** Productos con saldo observado en destino, por resolver (ALM-03). */
    @Query("select i from SalidaItem i join fetch i.producto left join fetch i.variante join fetch i.salida " +
            "where i.destObservedQty > 0")
    List<SalidaItem> findConObservado();

    /** Despachado por item de un REQ, en una sola consulta: [requestItemId, suma]. Sin salidas anuladas (DEC-L088). */
    @Query("select i.requerimientoItem.requestItemId, sum(i.qty) from SalidaItem i " +
            "where i.requerimientoItem.requerimiento.requestId = :requestId and i.salida.status <> 'ANULADA' " +
            "group by i.requerimientoItem.requestItemId")
    List<Object[]> sumarDespachadoPorItem(@Param("requestId") UUID requestId);

    /** RF-079: entregado = aceptado en destino (ALM-03, v37ReceivedQty) por item de un REQ: [requestItemId, suma]. */
    @Query("select i.requerimientoItem.requestItemId, sum(i.destAcceptedQty) from SalidaItem i " +
            "where i.requerimientoItem.requerimiento.requestId = :requestId " +
            "group by i.requerimientoItem.requestItemId")
    List<Object[]> sumarEntregadoPorItem(@Param("requestId") UUID requestId);

    @Query("select coalesce(sum(i.qty), 0) from SalidaItem i where i.requerimientoItem.requestItemId = :requestItemId " +
            "and i.salida.status <> 'ANULADA'")
    BigDecimal sumarDespachado(@Param("requestItemId") UUID requestItemId);

    @Query("select i from SalidaItem i join fetch i.producto left join fetch i.variante join fetch i.salida s " +
            "join fetch i.requerimientoItem where s.requerimiento.requestId = :requestId and s.status <> 'ANULADA'")
    List<SalidaItem> findByRequerimiento(@Param("requestId") UUID requestId);
}
