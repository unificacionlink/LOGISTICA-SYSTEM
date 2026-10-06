package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DespachoItemInput(
        @NotNull(message = "Falta el item del requerimiento.") UUID requestItemId,
        @NotNull(message = "Indica la cantidad a despachar.")
        @DecimalMin(value = "0", message = "La cantidad no puede ser negativa.")
        BigDecimal qty) {
}
