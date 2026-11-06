package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura OCR de una cotizacion para el comparativo (DEC-L047): proveedor
 * reconocido por su RUC y costo unitario propuesto por consolidado. Son
 * sugerencias; Logistica las revisa antes de guardar.
 */
public record OcrComparativoDto(
        UUID supplierId,
        String proveedorDetectado,
        String rucDetectado,
        LocalDate fechaDetectada,
        String monedaDetectada,
        BigDecimal totalDetectado,
        Integer plazoDetectadoDias,
        Map<UUID, BigDecimal> precios,
        String textoDetectado) {
}
