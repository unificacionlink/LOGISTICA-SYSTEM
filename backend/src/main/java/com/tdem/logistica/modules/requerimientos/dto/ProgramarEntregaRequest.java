package com.tdem.logistica.modules.requerimientos.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ProgramarEntregaRequest(@NotNull(message = "Indica la fecha programada.") LocalDate fecha) {
}
