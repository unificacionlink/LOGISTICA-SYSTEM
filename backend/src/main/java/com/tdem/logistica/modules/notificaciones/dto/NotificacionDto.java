package com.tdem.logistica.modules.notificaciones.dto;

import java.time.Instant;
import java.util.UUID;

/** Aviso de la plataforma (DEC-L076). {@code leida} = ya la abrio. */
public record NotificacionDto(
        UUID id,
        UUID requestId,
        String requestCode,
        String tipo,
        String titulo,
        String detalle,
        Instant creadaEn,
        boolean leida) {
}
