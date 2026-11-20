package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Proveedor;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;

public interface ProveedorRepository extends RepositorioBase<Proveedor, UUID> {

    /** Con su empresa en la misma consulta (DEC-L081): sin una ida extra a la BD. */
    @EntityGraph(attributePaths = "empresa")
    List<Proveedor> findAllByActiveTrueOrderByLegalName();

    boolean existsByRuc(String ruc);
}
