package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CrearPeriodoRequest(@NotNull UUID cecoId, @NotNull @Min(2020) @Max(2100) Integer anio,
                                  @NotNull @Min(1) @Max(2) Integer semestre) {
}
