package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.NotNull;

public record CerrarPeriodoRequest(@NotNull Boolean cerrado) {
}
