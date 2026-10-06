package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EnvioDto(
        UUID shipmentId,
        String deliveryMode,
        String carrierName,
        String trackingCode,
        Integer packageCount,
        BigDecimal declaredWeight,
        String currencyCode,
        BigDecimal shippingCostNet,
        BigDecimal shippingTaxAmount,
        BigDecimal shippingCostTotal,
        LocalDate shipDate,
        LocalDate expectedDate,
        String status,
        Instant receivedAt,
        String receivedByName,
        String deliveryObservation) {
}
