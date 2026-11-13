package com.tdem.logistica.modules.registro;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.modules.registro.dto.RegistroDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consultas del registro de la plataforma, solo para ADMIN (DEC-L060):
 * actividad e ingresos, errores, cambios criticos (log_cambios) y movimientos
 * de inventario. Cada vista es una sola consulta paginada (la BD esta lejos).
 */
@Service
public class RegistroService {

    public static final int POR_PAGINA = 50;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAX_DIAS = 366;
    private static final int ERRORES_NAVEGADOR_POR_MINUTO = 20;

    private final JdbcTemplate jdbc;
    private final RegistroPlataforma registro;
    private final ConcurrentHashMap<UUID, long[]> reportes = new ConcurrentHashMap<>();

    public RegistroService(JdbcTemplate jdbc, RegistroPlataforma registro) {
        this.jdbc = jdbc;
        this.registro = registro;
    }

    /** Filtros comunes: rango de fechas (dias de Lima, inclusive), usuario, texto y un tipo propio de cada vista. */
    public record Filtros(LocalDate desde, LocalDate hasta, UUID usuarioId, String q, String tipo, int pagina) {
    }

    // ---------------------------------------------------------------- vistas

    public RegistroDto.Pagina<RegistroDto.Actividad> actividad(Filtros f, UsuarioPrincipal p) {
        exigirAdmin(p);
        Consulta c = new Consulta("a.created_at", f);
        c.usuario("a.user_id");
        c.texto("a.path", "a.detail", "a.ip", "u.display_name", "u.corporate_email");
        switch (tipo(f)) {
            case "INGRESOS" -> c.donde("a.path in ('/api/auth/login', '/api/auth/logout')");
            case "INGRESOS_FALLIDOS" -> c.donde("a.path = '/api/auth/login' and a.status >= 400");
            case "RECHAZADAS" -> c.donde("a.status >= 400");
            default -> { }
        }
        return c.pagina("""
                select a.id, a.created_at, a.user_id, u.display_name, u.corporate_email, a.method, a.path, a.module,
                  a.entity_id, a.status, a.duration_ms, a.ip, a.user_agent, a.detail
                from registro_actividad a left join usuario u on u.user_id = a.user_id""",
                (rs, i) -> new RegistroDto.Actividad(rs.getObject(1, UUID.class), instante(rs.getTimestamp(2)),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getObject(9, UUID.class), rs.getInt(10), rs.getInt(11), rs.getString(12),
                        rs.getString(13), rs.getString(14)));
    }

    public RegistroDto.Pagina<RegistroDto.Error> errores(Filtros f, UsuarioPrincipal p) {
        exigirAdmin(p);
        Consulta c = new Consulta("e.created_at", f);
        c.usuario("e.user_id");
        c.texto("e.path", "e.message", "e.error_type", "u.display_name");
        switch (tipo(f)) {
            case "SERVIDOR" -> c.donde("e.origin = 'SERVIDOR'");
            case "NAVEGADOR" -> c.donde("e.origin = 'NAVEGADOR'");
            default -> { }
        }
        return c.pagina("""
                select e.id, e.created_at, e.origin, e.user_id, u.display_name, e.method, e.path, e.status,
                  e.error_type, e.message, e.stack, e.user_agent
                from registro_error e left join usuario u on u.user_id = e.user_id""",
                (rs, i) -> new RegistroDto.Error(rs.getObject(1, UUID.class), instante(rs.getTimestamp(2)),
                        rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
                        rs.getString(7), (Integer) rs.getObject(8), rs.getString(9), rs.getString(10),
                        rs.getString(11), rs.getString(12)));
    }

    public RegistroDto.Pagina<RegistroDto.Cambio> cambios(Filtros f, UsuarioPrincipal p) {
        exigirAdmin(p);
        Consulta c = new Consulta("l.created_at", f);
        c.usuario("l.usuario_id");
        c.texto("l.entidad", "l.accion", "u.display_name", "l.antes::text", "l.despues::text");
        return c.pagina("""
                select l.id, l.created_at, l.usuario_id, u.display_name, l.entidad, l.entidad_id, l.accion,
                  l.antes::text, l.despues::text
                from log_cambios l left join usuario u on u.user_id = l.usuario_id""",
                (rs, i) -> new RegistroDto.Cambio(rs.getObject(1, UUID.class), instante(rs.getTimestamp(2)),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class),
                        rs.getString(7), rs.getString(8), rs.getString(9)));
    }

    public RegistroDto.Pagina<RegistroDto.Movimiento> movimientos(Filtros f, UsuarioPrincipal p) {
        exigirAdmin(p);
        Consulta c = new Consulta("m.created_at", f);
        c.usuario("m.created_by");
        c.texto("p.sku_base", "p.name", "v.internal_variant_code", "u.display_name", "m.reference_type");
        String t = tipo(f);
        if (!t.isEmpty() && !"TODO".equals(t)) {
            c.donde("m.movement_type = ?", t);
        }
        return c.pagina("""
                select m.inventory_movement_id, m.created_at, m.created_by, u.display_name, e.code, a.name,
                  coalesce(v.internal_variant_code, p.sku_base), p.name, m.movement_type, m.qty, m.unit_cost,
                  m.reference_type, m.reference_id
                from movimiento_inventario m
                join producto p on p.product_id = m.product_id
                left join producto_variante v on v.variant_id = m.variant_id
                join almacen a on a.warehouse_id = m.warehouse_id
                join empresa e on e.company_id = m.company_id
                left join usuario u on u.user_id = m.created_by""",
                (rs, i) -> new RegistroDto.Movimiento(rs.getObject(1, UUID.class), instante(rs.getTimestamp(2)),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), rs.getBigDecimal(10),
                        rs.getBigDecimal(11), rs.getString(12), rs.getObject(13, UUID.class)));
    }

    public RegistroDto.Resumen resumen(UsuarioPrincipal p) {
        exigirAdmin(p);
        return jdbc.queryForObject("""
                select
                  (select count(*) from registro_actividad where created_at > now() - interval '24 hours'),
                  (select count(*) from registro_actividad where created_at > now() - interval '24 hours'
                     and status >= 400),
                  (select count(*) from registro_actividad where created_at > now() - interval '24 hours'
                     and path = '/api/auth/login' and status >= 400),
                  (select count(*) from registro_error where created_at > now() - interval '24 hours'
                     and origin = 'SERVIDOR'),
                  (select count(*) from registro_error where created_at > now() - interval '24 hours'
                     and origin = 'NAVEGADOR')""",
                (rs, i) -> new RegistroDto.Resumen(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        rs.getLong(5)));
    }

    // ---------------------------------------------------------------- errores del navegador

    /** Cualquier usuario con sesion reporta los errores de su pantalla; con tope por minuto. */
    public void reportarErrorNavegador(String mensaje, String pila, String ruta, String navegador,
                                       UsuarioPrincipal p) {
        long minuto = System.currentTimeMillis() / 60_000;
        long[] ventana = reportes.compute(p.userId(), (k, v) -> v == null || v[0] != minuto ? new long[]{minuto, 0} : v);
        if (++ventana[1] > ERRORES_NAVEGADOR_POR_MINUTO) {
            return;
        }
        if (reportes.size() > 5_000) {
            reportes.entrySet().removeIf(e -> e.getValue()[0] < minuto);
        }
        registro.error(new RegistroPlataforma.Error(Instant.now(), "NAVEGADOR", p.userId(), null,
                RegistroPlataforma.corto(ruta, 500), null, "Error en la pantalla",
                RegistroPlataforma.corto(mensaje, 2_000), RegistroPlataforma.corto(pila, 8_000),
                RegistroPlataforma.corto(navegador, 300)));
    }

    // ---------------------------------------------------------------- utilidades

    private static void exigirAdmin(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.ADMIN)) {
            throw ApiException.forbidden("Solo un administrador puede ver el registro de la plataforma.");
        }
    }

    private static String tipo(Filtros f) {
        return f.tipo() == null ? "" : f.tipo().trim().toUpperCase(Locale.ROOT);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    /** Arma el where comun (fechas, usuario, texto) y la paginacion "una de mas" para saber si hay otra pagina. */
    private final class Consulta {
        private final List<String> condiciones = new ArrayList<>();
        private final List<Object> args = new ArrayList<>();
        private final String fecha;
        private final Filtros f;

        Consulta(String fecha, Filtros f) {
            this.fecha = fecha;
            this.f = f;
            LocalDate hasta = f.hasta() != null ? f.hasta() : LocalDate.now(LIMA);
            LocalDate desde = f.desde() != null ? f.desde() : hasta.minusDays(6);
            if (desde.isAfter(hasta)) {
                throw ApiException.badRequest("La fecha inicial es posterior a la final.");
            }
            if (desde.isBefore(hasta.minusDays(MAX_DIAS))) {
                throw ApiException.badRequest("El rango máximo es de un año.");
            }
            donde(fecha + " >= ?", Timestamp.from(desde.atStartOfDay(LIMA).toInstant()));
            donde(fecha + " < ?", Timestamp.from(hasta.plusDays(1).atStartOfDay(LIMA).toInstant()));
        }

        void donde(String condicion, Object... valores) {
            condiciones.add(condicion);
            args.addAll(List.of(valores));
        }

        void usuario(String columna) {
            if (f.usuarioId() != null) {
                donde(columna + " = ?", f.usuarioId());
            }
        }

        void texto(String... columnas) {
            if (f.q() == null || f.q().isBlank()) {
                return;
            }
            String patron = "%" + f.q().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> partes = new ArrayList<>();
            for (String col : columnas) {
                partes.add(col + " ilike ?");
                args.add(patron);
            }
            condiciones.add("(" + String.join(" or ", partes) + ")");
        }

        <T> RegistroDto.Pagina<T> pagina(String select, RowMapper<T> mapper) {
            int pagina = Math.max(0, f.pagina());
            List<Object> todos = new ArrayList<>(args);
            todos.add(POR_PAGINA + 1);
            todos.add(pagina * POR_PAGINA);
            List<T> filas = jdbc.query(select + " where " + String.join(" and ", condiciones)
                    + " order by " + fecha + " desc limit ? offset ?", mapper, todos.toArray());
            boolean hayMas = filas.size() > POR_PAGINA;
            return new RegistroDto.Pagina<>(hayMas ? filas.subList(0, POR_PAGINA) : filas, hayMas);
        }
    }
}
