package com.tdem.logistica.modules.stock.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** Stock minimo del SKU; null lo quita. */
public record MinimoStockRequest(@DecimalMin(value = "0", message = "El minimo no puede ser negativo.") BigDecimal minimo) {
}
