package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** RF-004: el solicitante pide el alta de un producto que no esta en el catalogo. */
public record CrearSolicitudRequest(
        @NotNull(message = "Selecciona la familia.") UUID familyId,
        @NotBlank(message = "Describe el producto.") @Size(max = 220, message = "Nombre demasiado largo.") String nombre,
        @NotNull(message = "Selecciona la unidad.") UUID uomId,
        String atributoTipo,
        String atributoValor,
        String descripcionTecnica,
        @NotBlank(message = "Indica para que se necesita.") String justificacion) {
}
