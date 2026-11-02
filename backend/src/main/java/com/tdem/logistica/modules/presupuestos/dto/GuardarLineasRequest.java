package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Reemplaza todas las partidas de la version de trabajo. */
public record GuardarLineasRequest(@NotNull @Size(max = 1000) List<@Valid @NotNull LineaRequest> lineas) {
}
