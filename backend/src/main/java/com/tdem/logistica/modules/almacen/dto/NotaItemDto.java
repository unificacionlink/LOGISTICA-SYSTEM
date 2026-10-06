package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;

public record NotaItemDto(
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        BigDecimal qty,
        BigDecimal purchaseCostReference) {
}
