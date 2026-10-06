package com.tdem.logistica.modules.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record AjusteStockRequest(
        @NotNull(message = "Selecciona una empresa.") UUID companyId,
        @NotNull(message = "Selecciona un producto valido.") UUID variantId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0.")
        BigDecimal qty,
        String comment) {
}
