package com.tdem.logistica.repository;

import com.tdem.logistica.entity.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, UUID> {

    @Query("select coalesce(sum(m.qty), 0) from MovimientoInventario m " +
            "where m.almacen.warehouseId = :warehouseId and m.producto.productId = :productId " +
            "and ((:variantId is null and m.variante is null) or m.variante.variantId = :variantId)")
    BigDecimal sumarKardex(
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId,
            @Param("variantId") UUID variantId);

    /** [variantId, saldo fisico] por variante del almacen. */
    @Query("select v.variantId, sum(m.qty) from MovimientoInventario m join m.variante v " +
            "where m.almacen.warehouseId = :warehouseId group by v.variantId")
    List<Object[]> sumarPorVariante(@Param("warehouseId") UUID warehouseId);
}
