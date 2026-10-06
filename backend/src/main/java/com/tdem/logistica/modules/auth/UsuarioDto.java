package com.tdem.logistica.modules.auth;

import java.util.List;
import java.util.UUID;

public record UsuarioDto(
        UUID id,
        String nombre,
        String correo,
        UUID companyId,
        String empresaCode,
        List<String> roles,
        boolean debeCambiarPassword) {
}
