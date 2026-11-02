package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SKU base activo con su precio de venta vigente en la empresa del presupuesto (null = sin precio). */
public record ProductoPresupuestoDto(UUID productId, String sku, String nombre, String familia, String unidad,
                                     BigDecimal precio) {
}
