package com.tdem.logistica.modules.compras.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RfqDto(
        UUID rfqId,
        String rfqCode,
        UUID requestId,
        String requestCode,
        UUID supplierId,
        String proveedorNombre,
        LocalDate issueDate,
        String paymentCondition,
        String deliveryMode,
        String deliveryLocation,
        Integer requestedLeadDays,
        String currencyRequested,
        String status,
        Instant createdAt) {
}
