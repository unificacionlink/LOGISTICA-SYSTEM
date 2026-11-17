package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Alta o edicion de una persona del padron (DEC-L064), solo en empresas que
 * RR.HH. aun no cubre. Al editar, {@code empresaCode} y {@code dni} no cambian
 * (son la clave de la persona) y se ignoran.
 */
public record PersonaRequest(
        String empresaCode,
        String dni,
        @NotBlank(message = "Ingresa el nombre completo.") @Size(max = 180, message = "Nombre demasiado largo.") String nombre,
        @Email(message = "Correo invalido.") @Size(max = 180, message = "Correo demasiado largo.") String correo,
        @Size(max = 150, message = "Cargo demasiado largo.") String cargo,
        UUID cecoId) {
}
