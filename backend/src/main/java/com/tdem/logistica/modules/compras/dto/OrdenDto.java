package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orden de compra. Desde el Lote 17 (DEC-L061) trae su aprobacion, su pago y
 * su liberacion; {@code bloqueoRecepcion} dice por que el almacen todavia no
 * puede recibirla (null si puede o si ya no aplica).
 */
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
        Instant createdAt,
        UUID createdById,
        String createdByName,
        Instant approvedAt,
        String paymentStatus,
        String paymentProofUrl,
        Instant paidAt,
        Instant releasedAt,
        String releaseNote,
        String bloqueoRecepcion,
        // Formato F-LOG-09 (DEC-L079): VALOR TOTAL, IGV y PRECIO TOTAL (= total).
        String taxMode,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        String deliveryTerm,
        String additionalSpecs,
        // OC directa (DEC-L080)
        String cecoCode,
        String cecoNombre,
        boolean servicesOnly) {
}
