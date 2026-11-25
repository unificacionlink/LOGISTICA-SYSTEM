package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;

public record NotaItemDto(
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        BigDecimal qty,
        BigDecimal purchaseCostReference,
        java.util.UUID issueItemId,
        /* Recepcion en destino (ALM-03): recibido = aceptado + observado + devuelto. */
        BigDecimal destRecibido,
        BigDecimal destAceptado,
        BigDecimal destObservado,
        BigDecimal destDevuelto,
        /* DEC-L086: precio de venta de TDEM a la empresa del destino (null si no es venta o no hay precio). */
        BigDecimal saleUnitPrice) {
}
