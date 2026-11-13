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
        boolean debeCambiarPassword,
        /** Recorridos guiados ya vistos o saltados (DEC-L055). */
        List<String> tutorialesVistos) {
}
