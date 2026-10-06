package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Requerimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RequerimientoRepository extends JpaRepository<Requerimiento, UUID> {

    @Query("select r from Requerimiento r " +
            "join fetch r.empresa join fetch r.ceco c join fetch c.empresa " +
            "left join fetch r.requester " +
            "where r.requester.userId = :requesterId order by r.createdAt desc")
    List<Requerimiento> findByRequester(UUID requesterId);

    @Query("select r from Requerimiento r " +
            "join fetch r.empresa join fetch r.ceco c join fetch c.empresa " +
            "left join fetch r.requester " +
            "order by r.createdAt desc")
    List<Requerimiento> findAllConDetalle();

    @Query("select r from Requerimiento r " +
            "join fetch r.empresa join fetch r.ceco c join fetch c.empresa " +
            "left join fetch r.requester " +
            "where r.requestId in :ids")
    List<Requerimiento> findByIdsConDetalle(List<UUID> ids);

    @Query("select r from Requerimiento r " +
            "join fetch r.empresa join fetch r.ceco c join fetch c.empresa " +
            "left join fetch r.requester " +
            "where r.status = :status order by r.createdAt")
    List<Requerimiento> findByStatusConDetalle(String status);

    @Query("select r from Requerimiento r " +
            "join fetch r.empresa join fetch r.ceco c join fetch c.empresa " +
            "left join fetch r.requester " +
            "where r.status in :statuses order by r.createdAt")
    List<Requerimiento> findByStatusInConDetalle(List<String> statuses);
}
