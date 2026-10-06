package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrdenDto(
        UUID orderId,
        String orderCode,
        UUID requestId,
        String requestCode,
        String proveedorNombre,
        String orderType,
        LocalDate issueDate,
        String paymentCondition,
        String deliveryMode,
        String deliveryLocation,
        String currencyCode,
        BigDecimal total,
        String status,
        Instant createdAt) {
}
