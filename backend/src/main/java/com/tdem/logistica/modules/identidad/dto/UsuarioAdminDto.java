package com.tdem.logistica.modules.identidad.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UsuarioAdminDto(
        UUID id,
        String nombre,
        String correo,
        String dni,
        String empresaCode,
        String cargo,
        List<String> roles,
        List<UUID> cecoIds,
        String fuente,
        boolean activo,
        Instant sincronizadoEn,
        boolean tieneAcceso,
        boolean debeCambiarPassword) {
}
