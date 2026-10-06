package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {

    List<Proveedor> findAllByActiveTrueOrderByLegalName();

    boolean existsByRuc(String ruc);
}
