package com.tdem.logistica.modules.stock.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaldoStockDto(
        UUID variantId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        BigDecimal fisico,
        BigDecimal reservado,
        BigDecimal disponible) {
}
