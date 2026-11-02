package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CrearPresupuestoRequest(@NotNull UUID periodId, @Size(max = 500) String observaciones) {
}
