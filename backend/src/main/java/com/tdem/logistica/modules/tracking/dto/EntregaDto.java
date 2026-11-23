package com.tdem.logistica.modules.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una fila por Nota de Inventario y su envio (RF-081), o por recepcion de una
 * entrega directa del proveedor al CECO (DEC-L084: sin NI, con {@code orderId}).
 */
public record EntregaDto(
        int posicion,
        UUID inventoryNoteId,
        String notaCodigo,
        Instant emitidaEn,
        String itemsDespachados,
        BigDecimal valorNota,
        UUID shipmentId,
        String modalidad,
        String transportista,
        String codigoSeguimiento,
        LocalDate fechaEnvio,
        String moneda,
        BigDecimal costoEnvioNeto,
        String estadoEntrega,
        Instant recepcionadoEn,
        String urlGuia,
        String urlTicket,
        String urlConformidad,
        UUID orderId) {
}
