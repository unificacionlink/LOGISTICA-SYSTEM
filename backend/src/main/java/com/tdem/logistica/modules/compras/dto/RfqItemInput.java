package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RfqItemInput(
        @NotNull(message = "Falta el item a cotizar.") UUID requestItemId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0.")
        BigDecimal qty,
        String technicalSpecification) {
}
