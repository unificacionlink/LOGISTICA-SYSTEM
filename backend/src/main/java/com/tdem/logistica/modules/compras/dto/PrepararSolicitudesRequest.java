package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PrepararSolicitudesRequest(
        @NotEmpty(message = "Selecciona al menos un consolidado.") List<UUID> consolidationIds) {
}
