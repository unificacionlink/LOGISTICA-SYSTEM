package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** decision = APROBADO u OBSERVADO; observar exige motivo. */
public record DecidirPresupuestoRequest(@NotBlank String decision, @Size(max = 500) String motivo) {
}
