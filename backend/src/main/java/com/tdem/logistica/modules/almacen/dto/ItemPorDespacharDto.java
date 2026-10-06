package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPorDespacharDto(
        UUID requestItemId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        String unidadMedida,
        BigDecimal qtyRequested,
        BigDecimal qtyDespachado,
        BigDecimal qtyEntregado,
        BigDecimal qtyPorDespachar) {
}
