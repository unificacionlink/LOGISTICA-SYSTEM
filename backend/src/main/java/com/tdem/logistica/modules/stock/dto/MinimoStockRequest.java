package com.tdem.logistica.modules.stock.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Stock minimo del SKU; null lo quita. */
public record MinimoStockRequest(
        @DecimalMin(value = "0", message = "El minimo no puede ser negativo.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.")
        BigDecimal minimo) {
}
