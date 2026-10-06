package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RfqItemDto(
        UUID rfqItemId,
        UUID requestItemId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        String unidadMedida,
        BigDecimal qty,
        String technicalSpecification) {
}
