package com.tdem.logistica.modules.requerimientos.dto;

import java.time.OffsetDateTime;

public record ObservacionDto(String motivo, String observadoPor, OffsetDateTime fecha) {
}
