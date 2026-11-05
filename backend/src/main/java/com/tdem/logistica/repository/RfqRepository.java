package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Rfq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfqRepository extends JpaRepository<Rfq, UUID> {

    /** RFQ que cotizan algo del requerimiento: las previas al Lote 16 (request_id) o por sus necesidades. */
    @Query("select r from Rfq r join fetch r.proveedor where r.requerimiento.requestId = :requestId " +
            "or exists (select 1 from RfqItem i, NecesidadCompra n where i.rfq = r " +
            "  and n.consolidacion = i.consolidacion and n.requerimientoItem.requerimiento.requestId = :requestId) " +
            "order by r.createdAt desc")
    List<Rfq> findPorRequerimiento(@Param("requestId") UUID requestId);

    @Query("select r from Rfq r join fetch r.proveedor join fetch r.empresa left join fetch r.requerimiento " +
            "where r.rfqId = :rfqId")
    Optional<Rfq> findDetalleById(UUID rfqId);

    /** Por enviar, enviadas y respondidas: lo que Logistica tiene que mover. */
    @Query("select r from Rfq r join fetch r.proveedor join fetch r.empresa " +
            "where r.status in ('BORRADOR','ENVIADA','RESPONDIDA') order by r.createdAt desc")
    List<Rfq> findAbiertas();

    /** RFQ preparada y aun no enviada de la empresa al proveedor: recibe nuevas lineas (DEC-L039). */
    @Query("select r from Rfq r where r.empresa.companyId = :companyId and r.proveedor.supplierId = :supplierId " +
            "and r.status = 'BORRADOR' and r.requerimiento is null order by r.createdAt")
    List<Rfq> findBorradores(@Param("companyId") UUID companyId, @Param("supplierId") UUID supplierId);
}
