package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Proveedor;

import java.util.List;
import java.util.UUID;

public interface ProveedorRepository extends RepositorioBase<Proveedor, UUID> {

    List<Proveedor> findAllByActiveTrueOrderByLegalName();

    boolean existsByRuc(String ruc);
}
