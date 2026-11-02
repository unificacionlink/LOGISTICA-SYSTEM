package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Partida: SKU base x 6 meses. {@code precio} = congelado si el presupuesto esta
 * APROBADO, si no el precio de venta vigente (null = sin precio registrado).
 */
public record LineaPresupuestoDto(UUID productId, String sku, String nombre, String familia, String unidad,
                                  List<BigDecimal> meses, BigDecimal cantidad, BigDecimal precio,
                                  BigDecimal importe, boolean precioCongelado) {
}
