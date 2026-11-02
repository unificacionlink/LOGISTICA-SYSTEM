package com.tdem.logistica.repository;

import com.tdem.logistica.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, UUID> {

    @Query("select v from ProductoVariante v join fetch v.producto p join fetch p.familia f join fetch f.tipo t " +
            "where v.active = true and p.active = true order by t.name, f.code, p.name, v.internalVariantCode")
    List<ProductoVariante> findCatalogoActivo();

    @Query("select v from ProductoVariante v join fetch v.producto p " +
            "where v.variantId in :ids and v.active = true and p.active = true")
    List<ProductoVariante> findActivasPorId(List<UUID> ids);

    @Query("select v from ProductoVariante v join fetch v.producto where v.variantId in :ids")
    List<ProductoVariante> findConProductoPorIds(List<UUID> ids);
}
