package com.tdem.logistica.repository;

import com.tdem.logistica.entity.ResolucionDestino;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResolucionDestinoRepository extends RepositorioBase<ResolucionDestino, UUID> {

    @Query("select r from ResolucionDestino r join fetch r.salidaItem si join fetch si.producto " +
            "left join fetch si.variante join fetch r.createdBy left join fetch r.receivedBy " +
            "where r.envio.shipmentId = :shipmentId order by r.createdAt")
    List<ResolucionDestino> findByEnvio(@Param("shipmentId") UUID shipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ResolucionDestino r where r.resolutionId = :id")
    Optional<ResolucionDestino> bloquear(@Param("id") UUID id);

    /** Devoluciones (en camino o en cuarentena), para el almacen central. */
    @Query("select r from ResolucionDestino r join fetch r.salidaItem si join fetch si.producto " +
            "left join fetch si.variante join fetch r.envio e join fetch e.notaInventario n " +
            "join fetch n.requerimiento join fetch n.empresa join fetch r.createdBy left join fetch r.receivedBy " +
            "where r.action = 'DEVOLVER' order by r.createdAt desc")
    List<ResolucionDestino> findDevoluciones();
}
