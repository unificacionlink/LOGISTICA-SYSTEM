package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Direccion nueva para la lista de lugares de entrega (DEC-L089); el CECO es opcional. */
public record CrearPuntoEntregaRequest(
        @NotBlank(message = "Indica un nombre para la dirección.")
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres.") String nombre,
        @NotBlank(message = "Indica la dirección.")
        @Size(max = 200, message = "La dirección admite hasta 200 caracteres.") String direccion,
        UUID cecoId) {
}
