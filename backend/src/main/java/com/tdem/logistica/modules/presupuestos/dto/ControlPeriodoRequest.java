package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.NotNull;

public record ControlPeriodoRequest(@NotNull Boolean obligatorio) {
}
