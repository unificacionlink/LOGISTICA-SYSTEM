package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrdenItemDto(
        UUID orderItemId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        String description,
        String unidadMedida,
        BigDecimal qtyOrdered,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        /** Como llama el proveedor a este producto en su cotizacion (DEC-L053). */
        String referenciaProveedor) {
}
