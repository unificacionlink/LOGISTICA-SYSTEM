package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RegistrarPrecioRequest(
        @NotNull(message = "Indica la empresa.") UUID companyId,
        @NotNull(message = "Ingresa un precio de venta mayor que cero.")
        @Positive(message = "Ingresa un precio de venta mayor que cero.")
        @Digits(integer = 10, fraction = 4, message = "Precio invalido (hasta 4 decimales).") BigDecimal precio) {
}
