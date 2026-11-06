package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecepcionItemInput(
        @NotNull(message = "Falta el item de la orden.") UUID orderItemId,
        @NotNull(message = "Indica la cantidad recibida (0 si no llego).")
        @DecimalMin(value = "0", message = "La cantidad recibida no puede ser negativa.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.")
        BigDecimal qtyReceived,
        String conditionStatus,
        String observation,
        @Valid IncidenciaInput incidencia) {
}
