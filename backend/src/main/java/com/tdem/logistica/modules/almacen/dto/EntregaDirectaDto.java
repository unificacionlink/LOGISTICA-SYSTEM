package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Entrega directa del proveedor al CECO de una OC (DEC-L084): lo que debe
 * llegar a cada CECO, lo ya recibido y las recepciones registradas.
 *
 * @param bloqueo por que todavia no se puede recibir (falta aprobar o liberar); null si se puede.
 */
public record EntregaDirectaDto(
        UUID orderId,
        String orderCode,
        String proveedor,
        String proveedorRuc,
        String estado,
        String bloqueo,
        List<Destino> destinos,
        List<RecepcionDto> recepciones,
        List<IncidenciaDto> incidencias) {

    public record Destino(UUID cecoId, String cecoCode, String cecoNombre, String empresaCode, List<Linea> lineas) {
    }

    /**
     * Lo que le toca a un item de requerimiento (o al CECO de una OC directa) de
     * una linea de la OC. {@code puedeRegistrar}: quien consulta puede recibirla.
     */
    public record Linea(
            UUID orderItemId,
            UUID needId,
            UUID requestItemId,
            UUID requestId,
            String requestCode,
            String solicitante,
            String sku,
            String descripcion,
            String unidad,
            BigDecimal asignado,
            BigDecimal recibido,
            BigDecimal pendiente,
            boolean puedeRegistrar) {
    }
}
