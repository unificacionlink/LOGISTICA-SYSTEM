package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncidenciaDto(
        UUID receiptIssueId,
        Integer receiptNumber,
        UUID orderItemId,
        String itemDescription,
        String issueType,
        BigDecimal qtyAffected,
        String description,
        String supplierAction,
        String status,
        Instant createdAt,
        Instant closedAt) {
}
