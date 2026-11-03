package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cumplimiento de un SKU (RES-01): cantidades y montos [programado, real,
 * pendiente, saldo, disponible] x precio congelado. Un monto null = SIN PRECIO
 * (no equivale a cero). {@code compra} es null si el usuario no ve costos.
 */
public record CumplimientoFilaDto(UUID productId, String familia, String subfamilia, String sku, String nombre,
                                  String unidad, boolean sinPresupuesto, BigDecimal programado, BigDecimal real,
                                  BigDecimal pendiente, BigDecimal saldo, BigDecimal disponible,
                                  BigDecimal precioVenta, List<BigDecimal> venta, BigDecimal precioCompra,
                                  List<BigDecimal> compra) {
}
