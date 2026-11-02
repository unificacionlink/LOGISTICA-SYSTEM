package com.tdem.logistica.modules.aprobaciones;

import com.tdem.logistica.common.AppConstants.EntidadAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Aprobacion;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.repository.AprobacionRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quien decide y quien ve un REQ como aprobador (DEC-L030, V38.7):
 * <ul>
 *   <li>Nadie decide su propio requerimiento.</li>
 *   <li>ADMIN decide cualquiera (respaldo si el aprobador falta).</li>
 *   <li>Con aprobador asignado: ese usuario (APROBADOR o GERENCIA) y la
 *       GERENCIA que tenga el CECO entre los suyos.</li>
 *   <li>Sin aprobador asignado: cualquier APROBADOR o GERENCIA.</li>
 * </ul>
 */
@Component
public class AmbitoAprobacion {

    private final AprobacionRepository aprobacionRepository;
    private final JdbcTemplate jdbc;

    public AmbitoAprobacion(AprobacionRepository aprobacionRepository, JdbcTemplate jdbc) {
        this.aprobacionRepository = aprobacionRepository;
        this.jdbc = jdbc;
    }

    public static boolean esDecisor(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.APROBADOR, Roles.GERENCIA, Roles.ADMIN);
    }

    /** CECOs del usuario (usuario_ceco): una consulta, para filtrar listas sin N+1. */
    public Set<UUID> cecosDe(UUID userId) {
        return new HashSet<>(jdbc.queryForList(
                "select ceco_id from usuario_ceco where user_id = ?", UUID.class, userId));
    }

    public boolean puedeDecidir(Aprobacion a, Requerimiento r, UsuarioPrincipal p, Set<UUID> cecosDelUsuario) {
        if (r.getRequester() != null && r.getRequester().getUserId().equals(p.userId())) {
            return false;
        }
        if (p.tieneAlgunRol(Roles.ADMIN)) {
            return true;
        }
        if (a.getAssignedTo() == null) {
            return p.tieneAlgunRol(Roles.APROBADOR, Roles.GERENCIA);
        }
        if (a.getAssignedTo().equals(p.userId())) {
            return p.tieneAlgunRol(Roles.APROBADOR, Roles.GERENCIA);
        }
        return p.tieneAlgunRol(Roles.GERENCIA) && cecosDelUsuario.contains(r.getCeco().getCecoId());
    }

    public boolean puedeDecidir(Aprobacion a, Requerimiento r, UsuarioPrincipal p) {
        return puedeDecidir(a, r, p, p.tieneAlgunRol(Roles.GERENCIA) ? cecosDe(p.userId()) : Set.of());
    }

    /**
     * Un aprobador ve un REQ enviado si le toca o le toco decidirlo, si no tiene
     * aprobador asignado, o (GERENCIA) si el CECO es suyo.
     */
    public boolean puedeVerComoAprobador(Requerimiento r, UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.APROBADOR, Roles.GERENCIA)
                || EstadoRequerimiento.BORRADOR.equals(r.getStatus())) {
            return false;
        }
        for (Aprobacion a : aprobacionRepository.findPorEntidad(EntidadAprobacion.REQUERIMIENTO, r.getRequestId())) {
            if (a.getAssignedTo() == null || p.userId().equals(a.getAssignedTo()) || p.userId().equals(a.getDecidedBy())) {
                return true;
            }
        }
        return p.tieneAlgunRol(Roles.GERENCIA) && cecosDe(p.userId()).contains(r.getCeco().getCecoId());
    }
}
