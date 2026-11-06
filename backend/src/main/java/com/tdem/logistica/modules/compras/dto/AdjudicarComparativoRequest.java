package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Ganador elegido en el comparativo para cada consolidado. */
public record AdjudicarComparativoRequest(
        @NotEmpty @Size(max = 40) List<UUID> consolidationIds,
        @NotEmpty(message = "Elige al menos un ganador.") @Valid List<Eleccion> elecciones) {

    public record Eleccion(@NotNull UUID consolidationId, @NotNull UUID quotationItemId) {
    }
}
