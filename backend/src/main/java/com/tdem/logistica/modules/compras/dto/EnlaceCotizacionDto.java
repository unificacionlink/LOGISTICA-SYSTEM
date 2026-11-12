package com.tdem.logistica.modules.compras.dto;

import java.time.Instant;

/**
 * Enlace del proveedor para una RFQ (DEC-L052), visto por Logistica. {@code url}
 * solo viene al generarlo: despues no se puede recuperar (se guarda el hash).
 */
public record EnlaceCotizacionDto(
        String url,
        String token,
        Instant creadoEl,
        Instant venceEl,
        Instant abiertoEl,
        Instant respondidoEl,
        boolean vigente) {
}
