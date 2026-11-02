package com.tdem.logistica.modules.presupuestos.dto;

import java.time.Instant;

public record HistorialPresupuestoDto(String accion, String usuario, Instant fecha, String motivo) {
}
