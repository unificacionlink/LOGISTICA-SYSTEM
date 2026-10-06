package com.tdem.logistica.modules.auth;

import jakarta.validation.constraints.NotBlank;

public record CambiarPasswordRequest(
        @NotBlank(message = "Ingresa tu contrasena actual.") String actual,
        @NotBlank(message = "Ingresa la nueva contrasena.") String nueva) {
}
