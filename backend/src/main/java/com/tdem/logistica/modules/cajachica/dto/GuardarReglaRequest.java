package com.tdem.logistica.modules.cajachica.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GuardarReglaRequest(
        @NotNull(message = "Indica el umbral.") @DecimalMin(value = "0", message = "El umbral no puede ser negativo.")
        @Digits(integer = 9, fraction = 2, message = "Monto invalido: hasta 9 digitos y 2 decimales.")
        BigDecimal umbral,
        boolean bajoMonto,
        boolean mismoDia,
        boolean fallaProveedor,
        boolean excepcion) {
}
