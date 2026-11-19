package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.security.UsuarioPrincipal;

/** El modulo de Proveedores lo usan solo Logistica y Admin (roles vigentes, DEC-L030). */
final class AccesoProveedores {

    private AccesoProveedores() {
    }

    static void exigir(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar proveedores.");
        }
    }

    /** Texto recortado o null si viene vacio. */
    static String limpio(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static String exigirMotivo(String motivo, String mensaje) {
        String m = limpio(motivo);
        if (m == null) {
            throw ApiException.badRequest(mensaje);
        }
        return m;
    }
}
