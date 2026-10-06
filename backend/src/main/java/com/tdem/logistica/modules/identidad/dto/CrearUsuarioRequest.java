package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Alta manual, solo para empresas que RRHH aun no cubre (DEC-L002). */
public record CrearUsuarioRequest(
        @NotBlank(message = "Ingresa el nombre.") String nombre,
        @NotBlank(message = "Ingresa el correo.") @Email(message = "Correo invalido.") String correo,
        String dni,
        @NotBlank(message = "Selecciona la empresa.") String empresaCode,
        UUID cecoId,
        @NotEmpty(message = "Asigna al menos un rol.") List<String> roles,
        String cargo) {
}
