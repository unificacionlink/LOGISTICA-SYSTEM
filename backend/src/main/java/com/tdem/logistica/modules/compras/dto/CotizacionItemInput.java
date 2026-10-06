package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CotizacionItemInput(
        @NotNull(message = "Falta el item cotizado.") UUID rfqItemId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0.")
        BigDecimal qty,
        @NotNull(message = "El costo unitario es obligatorio.")
        @DecimalMin(value = "0.0001", message = "El costo unitario debe ser mayor a 0.")
        BigDecimal unitCost,
        String offeredBrand,
        String offeredModel) {
}
