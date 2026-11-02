package com.tdem.logistica.modules.maestro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Precio de venta vigente de un SKU en una empresa (DEC-L035). */
public record PrecioVentaDto(UUID productId, BigDecimal precio, String actualizadoPor, Instant actualizadoEn) {
}
