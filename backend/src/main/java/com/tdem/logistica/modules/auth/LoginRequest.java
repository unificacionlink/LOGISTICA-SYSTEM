package com.tdem.logistica.modules.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Ingresa tu correo.") String correo,
        @NotBlank(message = "Ingresa tu contrasena.") String password) {
}
