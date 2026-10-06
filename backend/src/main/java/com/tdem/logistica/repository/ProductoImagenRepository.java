package com.tdem.logistica.repository;

import com.tdem.logistica.entity.ProductoImagen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, UUID> {

    @Query("select pi from ProductoImagen pi join fetch pi.documento " +
            "where pi.variantId is null and pi.primary = true and pi.active = true")
    List<ProductoImagen> findPrincipalesActivas();

    @Query("select pi from ProductoImagen pi where pi.producto.productId = :productId " +
            "and pi.variantId is null and pi.primary = true and pi.active = true")
    List<ProductoImagen> findPrincipalesActivasPorProducto(UUID productId);
}
