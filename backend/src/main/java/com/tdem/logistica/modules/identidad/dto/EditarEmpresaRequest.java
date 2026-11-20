package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EditarEmpresaRequest(
        @NotBlank(message = "Ingresa la razon social.")
        @Size(max = 180, message = "La razon social admite hasta 180 caracteres.") String nombre,
        @Pattern(regexp = "^$|^[0-9]{11}$", message = "El RUC tiene 11 digitos.") String ruc,
        @Size(max = 250, message = "La direccion admite hasta 250 caracteres.") String direccion,
        @Size(max = 40, message = "El telefono admite hasta 40 caracteres.") String telefono) {
}
