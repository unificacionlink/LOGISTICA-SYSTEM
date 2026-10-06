package com.tdem.logistica.repository;

import com.tdem.logistica.entity.FamiliaProducto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FamiliaProductoRepository extends JpaRepository<FamiliaProducto, UUID> {
    List<FamiliaProducto> findByActiveTrueOrderBySortOrder();
}
