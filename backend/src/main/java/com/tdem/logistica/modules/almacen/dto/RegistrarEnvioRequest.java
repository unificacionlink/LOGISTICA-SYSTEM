package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarEnvioRequest(
        @NotBlank(message = "Indica la modalidad de entrega.") String deliveryMode,
        String carrierName,
        String trackingCode,
        @Positive(message = "Los bultos deben ser mayor a 0.") Integer packageCount,
        @DecimalMin(value = "0", message = "El peso no puede ser negativo.") BigDecimal declaredWeight,
        String currencyCode,
        @DecimalMin(value = "0", message = "El costo no puede ser negativo.") BigDecimal shippingCostNet,
        @DecimalMin(value = "0", message = "El IGV no puede ser negativo.") BigDecimal shippingTaxAmount,
        LocalDate shipDate,
        LocalDate expectedDate) {
}
