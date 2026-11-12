package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una linea del documento del proveedor que trae importes (DEC-L053): la que
 * Logistica puede elegir para un producto en "Revisar cotizacion".
 * {@code descripcion} es su texto sin montos; {@code codigo}, el codigo del
 * proveedor si la linea empieza con uno.
 */
public record OcrLineaDto(int indice, String texto, String descripcion, String codigo, List<BigDecimal> importes) {
}
