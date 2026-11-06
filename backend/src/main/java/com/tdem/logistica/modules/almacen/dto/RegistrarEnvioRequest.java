package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.Digits;
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
        @DecimalMin(value = "0", message = "El peso no puede ser negativo.")
        @Digits(integer = 6, fraction = 3, message = "Peso invalido: hasta 6 digitos y 3 decimales.") BigDecimal declaredWeight,
        String currencyCode,
        @DecimalMin(value = "0", message = "El costo no puede ser negativo.") @Digits(integer = 9, fraction = 2, message = "Monto invalido: hasta 9 digitos y 2 decimales.") BigDecimal shippingCostNet,
        @DecimalMin(value = "0", message = "El IGV no puede ser negativo.") @Digits(integer = 9, fraction = 2, message = "Monto invalido: hasta 9 digitos y 2 decimales.") BigDecimal shippingTaxAmount,
        LocalDate shipDate,
        LocalDate expectedDate) {
}
