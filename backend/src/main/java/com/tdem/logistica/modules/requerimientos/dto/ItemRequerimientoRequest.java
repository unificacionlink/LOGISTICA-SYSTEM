package com.tdem.logistica.modules.requerimientos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemRequerimientoRequest(
        @NotNull(message = "Selecciona un producto valido.") UUID variantId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "1", message = "La cantidad debe ser al menos 1.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.")
        BigDecimal qtyRequested,
        String technicalRequirement,
        String observation) {
}
