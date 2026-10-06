package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CrearProductoRequest(
        @NotNull(message = "Selecciona la familia.") UUID familyId,
        @NotBlank(message = "Ingresa el nombre.") @Size(max = 220, message = "Nombre demasiado largo.") String nombre,
        @NotNull(message = "Selecciona la unidad.") UUID uomId,
        List<VarianteInput> variantes) {
}
