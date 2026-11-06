package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CotizacionItemInput(
        @NotNull(message = "Falta el item cotizado.") UUID rfqItemId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "1", message = "La cantidad debe ser al menos 1.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.")
        BigDecimal qty,
        @NotNull(message = "El costo unitario es obligatorio.")
        @DecimalMin(value = "0.0001", message = "El costo unitario debe ser mayor a 0.")
        @Digits(integer = 9, fraction = 4, message = "Costo invalido: hasta 9 digitos y 4 decimales.")
        BigDecimal unitCost,
        String offeredBrand,
        String offeredModel) {
}
