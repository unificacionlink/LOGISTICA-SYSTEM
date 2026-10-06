package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Logistica puede corregir nombre, familia y unidad antes de crear el SKU. */
public record AprobarSolicitudRequest(
        @NotNull(message = "Selecciona la familia.") UUID familyId,
        @NotBlank(message = "Ingresa el nombre.") String nombre,
        @NotNull(message = "Selecciona la unidad.") UUID uomId) {
}
