package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos propuestos por OCR/lectura de texto (Tesseract + PDFBox, gratuito y
 * local) a partir del documento subido. Son solo sugerencias: el usuario
 * siempre revisa y corrige antes de guardar la cotizacion (ningun campo se
 * persiste ciego). `textoDetectado` queda como respaldo tecnico para
 * verificar manualmente, no es el dato principal que ve Logistica.
 */
public record OcrCotizacionDto(
        String proveedorDetectado,
        String rucDetectado,
        LocalDate fechaDetectada,
        String monedaDetectada,
        BigDecimal totalDetectado,
        Integer plazoDetectadoDias,
        List<OcrItemSugeridoDto> itemsSugeridos,
        String textoDetectado) {
}
