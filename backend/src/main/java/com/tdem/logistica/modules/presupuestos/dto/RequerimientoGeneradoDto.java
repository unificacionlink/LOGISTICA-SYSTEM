package com.tdem.logistica.modules.presupuestos.dto;

import java.util.UUID;

/** REQ en borrador generado al aprobar el presupuesto, uno por mes (DEC-L049). */
public record RequerimientoGeneradoDto(UUID id, String codigo, int mes, String estado, int items) {
}
