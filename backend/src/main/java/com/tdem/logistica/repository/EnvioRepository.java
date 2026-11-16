package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Envio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvioRepository extends JpaRepository<Envio, UUID> {

    @Query("select e from Envio e left join fetch e.moneda left join fetch e.receivedBy " +
            "where e.notaInventario.inventoryNoteId = :noteId")
    Optional<Envio> findByNota(@Param("noteId") UUID noteId);

    @Query("select e from Envio e left join fetch e.moneda left join fetch e.receivedBy " +
            "where e.notaInventario.inventoryNoteId in :noteIds")
    List<Envio> findByNotas(@Param("noteIds") List<UUID> noteIds);

    @Query("select e from Envio e join fetch e.notaInventario n join fetch n.requerimiento " +
            "join fetch e.salida where e.shipmentId = :shipmentId")
    Optional<Envio> findDetalleById(@Param("shipmentId") UUID shipmentId);

    @Query("select e from Envio e join fetch e.salida join fetch e.notaInventario n join fetch n.requerimiento " +
            "join fetch n.empresa where e.salida.issueId in :issueIds")
    List<Envio> findBySalidas(@Param("issueIds") List<UUID> issueIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Envio e where e.shipmentId = :shipmentId")
    Optional<Envio> bloquear(@Param("shipmentId") UUID shipmentId);
}
