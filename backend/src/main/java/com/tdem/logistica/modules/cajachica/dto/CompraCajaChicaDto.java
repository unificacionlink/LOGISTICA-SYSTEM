package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Compra con la caja chica del proyecto (DEC-L092), con sus lineas y su factura. */
public record CompraCajaChicaDto(
        UUID id,
        String codigo,
        String empresaCode,
        String cecoCode,
        String cecoNombre,
        String solicitante,
        UUID solicitanteId,
        LocalDate fecha,
        String proveedorRuc,
        String proveedorNombre,
        String tipoComprobante,
        String serie,
        String numero,
        BigDecimal total,
        /** Archivo de la factura (foto o PDF) o el enlace indicado. */
        String facturaArchivoUrl,
        String facturaUrl,
        String nota,
        String estado,
        String motivoAnulacion,
        Instant registrada,
        List<Linea> lineas) {

    public record Linea(String sku, String descripcion, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal total) {
    }
}
