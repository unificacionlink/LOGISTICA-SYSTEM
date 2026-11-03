package com.tdem.logistica.modules.presupuestos.dto;

import java.util.List;
import java.math.BigDecimal;

/** Montos por familia (o TOTAL); un elemento null = INCOMPLETO (alguna fila sin precio). */
public record ResumenFamiliaDto(String familia, List<BigDecimal> venta, List<BigDecimal> compra) {
}
