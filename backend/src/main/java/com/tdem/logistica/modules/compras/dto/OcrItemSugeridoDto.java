package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Costo unitario sugerido para un item de la RFQ, encontrado buscando su codigo en el texto del documento. */
public record OcrItemSugeridoDto(UUID rfqItemId, String codigoVariante, BigDecimal costoSugerido) {
}
