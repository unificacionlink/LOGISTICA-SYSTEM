package com.tdem.logistica.modules.tracking.dto;

import java.time.Instant;

/** Historial del REQ, reconstruido desde las tablas de cada etapa. */
public record EventoDto(Instant fecha, String etapa, String accion, String usuario, String referencia) {
}
