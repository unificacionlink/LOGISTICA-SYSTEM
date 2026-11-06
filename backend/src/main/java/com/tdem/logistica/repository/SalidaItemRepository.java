package com.tdem.logistica.repository;

import com.tdem.logistica.entity.SalidaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalidaItemRepository extends JpaRepository<SalidaItem, UUID> {

    @Query("select i from SalidaItem i join fetch i.producto left join fetch i.variante " +
            "where i.salida.issueId = :issueId")
    List<SalidaItem> findBySalida(@Param("issueId") UUID issueId);

    /** Despachado por item de un REQ, en una sola consulta: [requestItemId, suma]. */
    @Query("select i.requerimientoItem.requestItemId, sum(i.qty) from SalidaItem i " +
            "where i.requerimientoItem.requerimiento.requestId = :requestId group by i.requerimientoItem.requestItemId")
    List<Object[]> sumarDespachadoPorItem(@Param("requestId") UUID requestId);

    /** Entregado (envio recepcionado o cerrado) por item de un REQ: [requestItemId, suma]. */
    @Query("select i.requerimientoItem.requestItemId, sum(i.qty) from SalidaItem i " +
            "where i.requerimientoItem.requerimiento.requestId = :requestId " +
            "and exists (select 1 from Envio e where e.salida = i.salida and e.status in ('RECEPCIONADO','CERRADO')) " +
            "group by i.requerimientoItem.requestItemId")
    List<Object[]> sumarEntregadoPorItem(@Param("requestId") UUID requestId);

    @Query("select coalesce(sum(i.qty), 0) from SalidaItem i where i.requerimientoItem.requestItemId = :requestItemId")
    BigDecimal sumarDespachado(@Param("requestItemId") UUID requestItemId);

    /** RF-079: solo cuenta lo despachado cuyo envio ya fue recepcionado en destino. */
    @Query("select coalesce(sum(i.qty), 0) from SalidaItem i " +
            "where i.requerimientoItem.requestItemId = :requestItemId " +
            "and exists (select 1 from Envio e where e.salida = i.salida and e.status in ('RECEPCIONADO','CERRADO'))")
    BigDecimal sumarEntregadoConfirmado(@Param("requestItemId") UUID requestItemId);

    @Query("select i from SalidaItem i join fetch i.producto left join fetch i.variante join fetch i.salida s " +
            "join fetch i.requerimientoItem where s.requerimiento.requestId = :requestId")
    List<SalidaItem> findByRequerimiento(@Param("requestId") UUID requestId);
}
