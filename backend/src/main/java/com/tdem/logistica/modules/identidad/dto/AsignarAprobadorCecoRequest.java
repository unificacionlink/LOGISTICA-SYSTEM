package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

/** Aprobador del CECO (DEC-L030). {@code userId} null = sin asignar (decide cualquier APROBADOR/GERENCIA). */
public record AsignarAprobadorCecoRequest(UUID userId) {
}
