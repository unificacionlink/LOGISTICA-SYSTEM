package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdjudicarRequest(@NotNull(message = "Selecciona la oferta a adjudicar.") UUID quotationItemId) {
}
