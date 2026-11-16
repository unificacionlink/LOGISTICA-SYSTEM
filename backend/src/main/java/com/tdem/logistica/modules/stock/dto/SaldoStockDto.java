package com.tdem.logistica.modules.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un SKU (variante) del catalogo en el almacen central del grupo (DEC-L045):
 * fisico (kardex), reservado, disponible, stock minimo (null = sin minimo) y en cuarentena.
 */
public record SaldoStockDto(
        UUID variantId,
        UUID productId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        UUID tipoId,
        String tipoNombre,
        UUID familiaId,
        String familiaNombre,
        String unidad,
        BigDecimal fisico,
        BigDecimal reservado,
        BigDecimal disponible,
        BigDecimal minimo,
        Instant ultimoMovimiento,
        /* Devuelto desde destino y recibido en cuarentena (ALM-03); no cuenta como disponible. */
        BigDecimal enCuarentena) {
}
