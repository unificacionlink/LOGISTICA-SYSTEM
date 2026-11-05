package com.tdem.logistica.modules.compras.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** requestId solo si la RFQ cotiza un unico requerimiento; requestCode lista los codigos. */
public record RfqDto(
        UUID rfqId,
        String rfqCode,
        UUID requestId,
        String requestCode,
        UUID supplierId,
        String proveedorNombre,
        String empresaCode,
        LocalDate issueDate,
        String paymentCondition,
        String deliveryMode,
        String deliveryLocation,
        Integer requestedLeadDays,
        String currencyRequested,
        String status,
        Instant createdAt,
        Instant sentAt,
        String sentChannel) {
}
