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
    Optional<Almacen> findFirstByEmpresa_CompanyIdAndWarehouseTypeAndActiveTrue(UUID companyId, String warehouseType);

    /** Serializa reservas automaticas concurrentes sobre el mismo almacen (DEC-L018). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Almacen a where a.warehouseId = :warehouseId")
    Optional<Almacen> bloquear(@Param("warehouseId") UUID warehouseId);
}
