package com.tdem.logistica.modules.aprobaciones.dto;

import jakarta.validation.constraints.NotBlank;

public record DecidirAprobacionRequest(
        @NotBlank(message = "La decision es obligatoria.") String decision,
        String comment,
        String decisionReason) {
}
