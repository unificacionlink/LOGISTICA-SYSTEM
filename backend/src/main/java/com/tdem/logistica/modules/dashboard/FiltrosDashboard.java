package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filtros comunes de las pestanas del dashboard (DEC-L020, DEC-L083): periodo
 * en dias hacia atras, empresa por codigo y, solo en Demanda, familia del
 * maestro ("t:<tipo>" = familia entera, "f:<familia>" = subfamilia, DEC-L034).
 * Tambien valida quien puede ver el dashboard.
 */
record FiltrosDashboard(int dias, String empresa, UUID tipo, UUID familia) {

    private static final Pattern CODIGO_EMPRESA = Pattern.compile("[A-Z0-9]{2,20}");

    static FiltrosDashboard de(int dias, String empresa, String familia, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN, Roles.APROBADOR, Roles.GERENCIA)) {
            throw ApiException.forbidden("No tienes permiso para ver el dashboard.");
        }
        if (dias < 7 || dias > 730) {
            throw ApiException.badRequest("El periodo debe estar entre 7 y 730 dias.");
        }
        String codigo = empresa == null || empresa.isBlank() ? null : empresa.trim().toUpperCase();
        if (codigo != null && !CODIGO_EMPRESA.matcher(codigo).matches()) {
            throw ApiException.badRequest("Empresa invalida.");
        }
        UUID tipo = null;
        UUID fam = null;
        if (familia != null && !familia.isBlank()) {
            String f = familia.trim();
            try {
                if (f.startsWith("t:")) {
                    tipo = UUID.fromString(f.substring(2));
                } else if (f.startsWith("f:")) {
                    fam = UUID.fromString(f.substring(2));
                } else {
                    throw ApiException.badRequest("Familia invalida.");
                }
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Familia invalida.");
            }
        }
        return new FiltrosDashboard(dias, codigo, tipo, fam);
    }

    /** :desde, :empresa, :tipo y :familia para las consultas (null = sin filtro). */
    MapSqlParameterSource parametros() {
        return new MapSqlParameterSource()
                .addValue("desde", OffsetDateTime.now().minusDays(dias))
                .addValue("empresa", empresa, Types.VARCHAR)
                .addValue("tipo", tipo, Types.OTHER)
                .addValue("familia", familia, Types.OTHER);
    }
}
