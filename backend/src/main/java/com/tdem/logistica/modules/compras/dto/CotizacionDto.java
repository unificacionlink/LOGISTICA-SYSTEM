package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CotizacionDto(
        UUID quotationId,
        UUID rfqId,
        UUID supplierId,
        String proveedorNombre,
        String supplierQuoteNumber,
        LocalDate issueDate,
        LocalDate expirationDate,
        String currencyCode,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        String paymentCondition,
        String deliveryMode,
        Integer leadDays,
        String status,
        String documentoUrl,
        List<CotizacionItemDto> items) {
}
