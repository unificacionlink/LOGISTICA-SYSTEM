package com.tdem.logistica.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado vigente de un usuario (activo + roles) para cada request (DEC-L024).
 * El JWT guarda los roles del momento del login; sin esto, un usuario
 * desactivado por el sync de RRHH (RF-041) o con roles cambiados por ADMIN
 * seguiria operando hasta que venza su token (8 h). Se consulta la BD a lo
 * sumo una vez por minuto por usuario (una sola consulta), y ADMIN/sync
 * invalidan la cache al cambiar algo.
 */
@Component
public class AccesoVigente {

    public record Acceso(boolean activo, List<String> roles) {
    }

    private record Entrada(Acceso acceso, long expira) {
    }

    private static final long TTL_MS = 60_000;

    private final JdbcTemplate jdbc;
    private final Map<UUID, Entrada> cache = new ConcurrentHashMap<>();

    public AccesoVigente(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Acceso obtener(UUID userId) {
        long ahora = System.currentTimeMillis();
        Entrada e = cache.get(userId);
        if (e != null && e.expira() > ahora) {
            return e.acceso();
        }
        Acceso a = consultar(userId);
        cache.put(userId, new Entrada(a, ahora + TTL_MS));
        return a;
    }

    public void invalidar(UUID userId) {
        cache.remove(userId);
    }

    public void invalidarTodo() {
        cache.clear();
    }

    private Acceso consultar(UUID userId) {
        List<String> roles = new ArrayList<>();
        boolean[] activo = {false};
        jdbc.query("""
                select u.active, r.code
                from usuario u
                left join usuario_rol ur on ur.user_id = u.user_id
                left join rol r on r.role_id = ur.role_id and r.active
                where u.user_id = ?""", rs -> {
            activo[0] = rs.getBoolean(1);
            String codigo = rs.getString(2);
            if (codigo != null && !roles.contains(codigo)) {
                roles.add(codigo);
            }
        }, userId);
        return new Acceso(activo[0], List.copyOf(roles));
    }
}
