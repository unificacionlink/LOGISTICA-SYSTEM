package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NecesidadDto(
        UUID needId,
        UUID requestId,
        String requestCode,
        String empresaCode,
        String cecoCode,
        String cecoNombre,
        String priority,
        LocalDate requiredDate,
        String skuBase,
        String codigoVariante,
        String nombreProducto,
        String atributo,
        String unidadMedida,
        BigDecimal qty,
        String status,
        String suggestedReason,
        UUID consolidationId,
        String consolidationCode,
        String consolidationStatus,
        Instant createdAt,
        /** DEC-L054: nota del solicitante al sugerir la caja chica del proyecto. */
        String notaSolicitante) {
}
