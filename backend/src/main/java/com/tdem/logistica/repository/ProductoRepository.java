package com.tdem.logistica.repository;

import com.tdem.logistica.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    @Query("select p from Producto p join fetch p.proveedorHabitual")
    List<Producto> findConProveedorHabitual();
}
