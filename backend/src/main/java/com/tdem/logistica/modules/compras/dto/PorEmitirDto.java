package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Adjudicado a una misma cotizacion y aun sin OC: se emite en una sola orden por proveedor. */
public record PorEmitirDto(
        UUID quotationId,
        String supplierQuoteNumber,
        String proveedorNombre,
        String empresaCode,
        String currencyCode,
        String paymentCondition,
        BigDecimal total,
        List<Linea> lineas) {

    public record Linea(
            UUID consolidationId,
            String consolidationCode,
            String skuBase,
            String codigoVariante,
            String nombreProducto,
            String unidadMedida,
            BigDecimal qty,
            BigDecimal unitCost,
            BigDecimal lineTotal) {
    }
}
