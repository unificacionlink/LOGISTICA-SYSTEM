package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPorRecibirDto(
        UUID orderItemId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        String description,
        String unidadMedida,
        BigDecimal qtyOrdered,
        BigDecimal qtyReceived,
        BigDecimal qtyPending) {
}
