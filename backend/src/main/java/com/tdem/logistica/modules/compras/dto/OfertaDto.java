package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Una fila del comparativo: la RFQ a un proveedor y, si respondio, su precio para el consolidado. */
public record OfertaDto(
        UUID rfqId,
        String rfqCode,
        String rfqStatus,
        String proveedorNombre,
        UUID quotationId,
        UUID quotationItemId,
        String supplierQuoteNumber,
        String cotizacionStatus,
        String currencyCode,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        String paymentCondition,
        String deliveryMode,
        Integer leadDays,
        String offeredBrand,
        String offeredModel,
        String documentoUrl,
        boolean adjudicada) {
}
