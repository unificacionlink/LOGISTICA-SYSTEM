package com.tdem.logistica.repository;

import com.tdem.logistica.entity.NotaInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaInventarioRepository extends JpaRepository<NotaInventario, UUID> {

    @Query("select coalesce(max(n.noteNumber), 0) from NotaInventario n where n.empresa.companyId = :companyId")
    Integer maxNumeroPorEmpresa(@Param("companyId") UUID companyId);

    @Query("select n from NotaInventario n join fetch n.requerimiento r join fetch n.empresa " +
            "left join fetch n.cecoDestino join fetch n.pickingUser order by n.createdAt desc")
    List<NotaInventario> findAllConDetalle();

    @Query("select n from NotaInventario n join fetch n.requerimiento r join fetch n.empresa " +
            "left join fetch n.cecoDestino join fetch n.pickingUser where r.requestId = :requestId " +
            "order by n.noteNumber")
    List<NotaInventario> findByRequerimiento(@Param("requestId") UUID requestId);

    @Query("select n from NotaInventario n join fetch n.requerimiento r join fetch n.empresa " +
            "left join fetch n.cecoDestino join fetch n.pickingUser join fetch n.salida " +
            "where n.inventoryNoteId = :noteId")
    Optional<NotaInventario> findDetalleById(@Param("noteId") UUID noteId);
}
