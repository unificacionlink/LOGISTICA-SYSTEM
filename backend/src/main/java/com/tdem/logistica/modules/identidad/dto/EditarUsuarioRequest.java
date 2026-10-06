package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Edicion de un usuario MANUAL (los de RR.HH. los mantiene el sync). Los CECOs van aparte (DEC-L029). */
public record EditarUsuarioRequest(
        @NotBlank(message = "Ingresa el nombre.") String nombre,
        @NotBlank(message = "Ingresa el correo.") @Email(message = "Correo invalido.") String correo,
        String dni,
        String cargo) {
}
