package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Envio;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
