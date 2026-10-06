package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.NotBlank;

public record RechazarSolicitudRequest(@NotBlank(message = "Indica el motivo.") String motivo) {
}
