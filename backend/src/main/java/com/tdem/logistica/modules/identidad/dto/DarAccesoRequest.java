package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Cuenta para una persona del padron (DEC-L041). */
public record DarAccesoRequest(
        @NotBlank(message = "Ingresa el correo.") @Email(message = "Correo invalido.") String correo,
        @NotEmpty(message = "Asigna al menos un rol.") List<String> roles) {
}
