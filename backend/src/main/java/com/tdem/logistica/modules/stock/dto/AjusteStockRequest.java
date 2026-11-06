package com.tdem.logistica.modules.stock.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Ajuste manual: cantidad con signo (ingreso positivo, salida negativa) y motivo obligatorio. */
public record AjusteStockRequest(
        @NotNull(message = "Selecciona un producto valido.") UUID variantId,
        @NotNull(message = "La cantidad es obligatoria.") @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.") BigDecimal qty,
        @NotBlank(message = "Indica el motivo del ajuste.") @Size(max = 300, message = "El motivo es muy largo.") String motivo) {
}
