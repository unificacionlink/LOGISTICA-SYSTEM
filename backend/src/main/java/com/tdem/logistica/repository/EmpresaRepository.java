package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Empresa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    Optional<Empresa> findByCode(String code);

    List<Empresa> findAllByActiveTrueOrderByCode();

    /** Serializa la numeracion correlativa por empresa (p. ej. Notas de Inventario). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.companyId = :companyId")
    Optional<Empresa> bloquear(@Param("companyId") UUID companyId);
}
