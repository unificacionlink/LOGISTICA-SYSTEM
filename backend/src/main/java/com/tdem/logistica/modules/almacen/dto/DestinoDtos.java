package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lectura de la recepcion en destino y sus diferencias (ALM-03, DEC-L063). */
public final class DestinoDtos {

    private DestinoDtos() {
    }

    public record RecepcionDto(
            UUID destinationReceiptId,
            int numero,
            Instant fecha,
            String recibidoPor,
            String resultado,
            String observacion,
            String enlaceConformidad,
            String documentoUrl,
            String documentoNombre,
            List<LineaDto> items) {
    }

    public record LineaDto(String sku, String nombreProducto, BigDecimal qty) {
    }

    public record ResolucionDto(
            UUID resolutionId,
            String sku,
            String nombreProducto,
            BigDecimal qty,
            String accion,
            String evidencia,
            String estado,
            Instant fecha,
            String registradoPor,
            Instant recibidaEn,
            String recibidaPor,
            String evidenciaRetorno) {
    }

    /** Producto con saldo observado en un envio, por resolver. */
    public record ObservadoDto(
            UUID shipmentId,
            UUID inventoryNoteId,
            String notaCodigo,
            String requestCode,
            String empresaCode,
            UUID issueItemId,
            String sku,
            String nombreProducto,
            BigDecimal observado) {
    }

    /** Devolucion hacia el almacen central (en camino o recibida en cuarentena). */
    public record DevolucionDto(
            UUID resolutionId,
            UUID inventoryNoteId,
            String notaCodigo,
            String requestCode,
            String empresaCode,
            String sku,
            String nombreProducto,
            BigDecimal qty,
            String estado,
            String evidencia,
            Instant fecha,
            Instant recibidaEn,
            String evidenciaRetorno) {
    }

    /** Pestana Diferencias del almacen (V38.7 aiView = 'diferencias'). */
    public record DiferenciasDto(List<ObservadoDto> observados, List<DevolucionDto> devoluciones) {
    }
}
