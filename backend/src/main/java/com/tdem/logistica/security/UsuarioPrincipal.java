package com.tdem.logistica.security;

import java.util.List;
import java.util.UUID;

/** Principal autenticado (equivalente a SessionUser en el stack anterior). */
public record UsuarioPrincipal(UUID userId, List<String> roles) {

    public boolean tieneAlgunRol(String... rolesPermitidos) {
        for (String r : rolesPermitidos) {
            if (roles.contains(r)) return true;
        }
        return false;
    }
}
