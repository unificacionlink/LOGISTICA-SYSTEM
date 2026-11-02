package com.tdem.logistica.repository;

import com.tdem.logistica.entity.FamiliaProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FamiliaProductoRepository extends JpaRepository<FamiliaProducto, UUID> {

    @Query("select f from FamiliaProducto f join fetch f.tipo t " +
            "where f.active = true and t.active = true order by t.name, f.code")
    List<FamiliaProducto> findActivasConTipo();
}
