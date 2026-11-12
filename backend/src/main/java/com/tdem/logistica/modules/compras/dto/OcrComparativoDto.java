package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
        String textoDetectado,
        /** Por consolidado: de que linea del documento salio el precio y como se encontro (DEC-L053). */
        Map<UUID, Coincidencia> coincidencias,
        /** Lineas del documento con importes, para elegir otra en la revision (DEC-L053). */
        List<OcrLineaDto> lineas) {

    /** origen: SKU, EQUIVALENCIA (aprendida de ese proveedor) o NOMBRE. */
    public record Coincidencia(Integer indiceLinea, String origen) {
    }
}
