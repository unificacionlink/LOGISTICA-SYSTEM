package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Costo unitario sugerido para un item, con la linea del documento de donde
 * salio ({@code indiceLinea}, ver {@link OcrLineaDto}) y como se encontro:
 * SKU (nuestro codigo), EQUIVALENCIA (lo aprendido de ese proveedor, DEC-L053)
 * o NOMBRE (palabras del nombre del producto).
 */
public record OcrItemSugeridoDto(UUID rfqItemId, String codigoVariante, BigDecimal costoSugerido,
                                 Integer indiceLinea, String origen) {
}
