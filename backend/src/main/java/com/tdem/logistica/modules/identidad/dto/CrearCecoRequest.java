package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearCecoRequest(
        @NotBlank(message = "Selecciona la empresa.") String empresaCode,
        @NotBlank(message = "Ingresa el codigo.") String code,
        @NotBlank(message = "Ingresa el nombre.") String nombre) {
}
