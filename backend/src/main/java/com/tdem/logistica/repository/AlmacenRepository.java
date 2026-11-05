package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Almacen;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AlmacenRepository extends JpaRepository<Almacen, UUID> {
    /** DEC-L045: el almacen central del grupo (TDEM), que atiende a todas las empresas. */
    @Query("select a from Almacen a join fetch a.empresa where a.groupCentral = true and a.active = true")
    Optional<Almacen> findCentralDelGrupo();

    /** Serializa reservas automaticas concurrentes sobre el mismo almacen (DEC-L018). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Almacen a where a.warehouseId = :warehouseId")
    Optional<Almacen> bloquear(@Param("warehouseId") UUID warehouseId);
}
