package com.tdem.logistica.modules.reportes;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.reportes.dto.ReporteObservacionDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reportes de observaciones desde la pantalla (DEC-L104). Cualquier usuario con
 * sesion reporta lo que ve (modulo, pantalla y la zona que senalo en el
 * esqueleto); ADMIN los revisa, responde y exporta a Excel para consolidarlos.
 */
@Service
public class ReporteObservacionService {

    public static final int POR_PAGINA = 50;
    private static final int MAX_POR_HORA = 30;
    private static final int MAX_EXCEL = 5_000;
    private static final long MAX_CAPTURA = 5L * 1024 * 1024;
    private static final Set<String> TIPOS_CAPTURA = Set.of("image/png", "image/jpeg");
    public static final Set<String> ESTADOS = Set.of("PENDIENTE", "EN_REVISION", "RESUELTO", "DESCARTADO");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final StorageClient storage;
    private final Auditoria auditoria;
    private final ConcurrentHashMap<UUID, long[]> envios = new ConcurrentHashMap<>();

    public ReporteObservacionService(JdbcTemplate jdbc, StorageClient storage, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.auditoria = auditoria;
    }

    /** Lo que manda la pantalla; la zona es la ruta de nombres del esqueleto (solo texto). */
    public record Nuevo(String modulo, String pantalla, String ruta, String zona, String descripcion,
                        String viewport) {
    }

    public record Imagen(byte[] contenido, String tipo) {
    }

    // ---------------------------------------------------------------- reportar

    @Transactional
    public ReporteObservacionDto.Creado reportar(Nuevo n, MultipartFile captura, String navegador,
                                                 UsuarioPrincipal p) {
        String descripcion = limpio(n.descripcion(), 2_000);
        if (descripcion == null) {
            throw ApiException.badRequest("Describe la observación.");
        }
        String ruta = limpio(n.ruta(), 500);
        if (ruta == null) {
            throw ApiException.badRequest("Falta la pantalla del reporte.");
        }
        controlarFrecuencia(p.userId());
        byte[] imagen = captura == null || captura.isEmpty() ? null : validarCaptura(captura);

        UUID id = UUID.randomUUID();
        String clave = null;
        if (imagen != null) {
            if (!storage.privadoConfigurado()) {
                throw ApiException.badRequest("Por ahora no se pueden adjuntar capturas; envía el reporte sin ella.");
            }
            String extension = "image/png".equals(captura.getContentType()) ? ".png" : ".jpg";
            clave = storage.subirPrivado("reportes/" + LocalDate.now(LIMA).toString().substring(0, 7) + "/" + id
                    + extension, imagen, captura.getContentType());
        }
        Integer numero = jdbc.queryForObject("""
                insert into reporte_observacion (id, user_id, module, screen, route, zone, description, capture_key,
                  user_agent, viewport)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning numero""", Integer.class,
                id, p.userId(), limpio(n.modulo(), 80), limpio(n.pantalla(), 200), ruta, limpio(n.zona(), 500),
                descripcion, clave, limpio(navegador, 300), limpio(n.viewport(), 20));
        return new ReporteObservacionDto.Creado(id, numero == null ? 0 : numero);
    }

    /** Los ultimos reportes de quien pregunta, con el estado y la respuesta del administrador. */
    public List<ReporteObservacionDto.Propio> mios(UsuarioPrincipal p) {
        return jdbc.query("""
                select id, numero, created_at, coalesce(screen, module), zone, description, status, response
                from reporte_observacion where user_id = ? order by created_at desc limit 20""",
                (rs, i) -> new ReporteObservacionDto.Propio(rs.getObject(1, UUID.class), rs.getInt(2),
                        rs.getTimestamp(3).toInstant(), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8)),
                p.userId());
    }

    // ---------------------------------------------------------------- administrador

    public ReporteObservacionDto.Pagina listar(String estado, String q, int pagina, UsuarioPrincipal p) {
        exigirAdmin(p);
        int pag = Math.max(0, pagina);
        Consulta c = consulta(estado, q);
        List<Object> args = new ArrayList<>(c.args);
        args.add(POR_PAGINA + 1);
        args.add(pag * POR_PAGINA);
        List<ReporteObservacionDto.Fila> filas = jdbc.query(SELECT + c.where() + " order by r.created_at desc limit ? offset ?",
                FILA, args.toArray());
        boolean hayMas = filas.size() > POR_PAGINA;
        return new ReporteObservacionDto.Pagina(hayMas ? filas.subList(0, POR_PAGINA) : filas, hayMas, conteo());
    }

    public byte[] excel(String estado, String q, UsuarioPrincipal p) {
        exigirAdmin(p);
        Consulta c = consulta(estado, q);
        List<Object> args = new ArrayList<>(c.args);
        args.add(MAX_EXCEL);
        // En el Excel, del primero al ultimo: se pega tal cual en la hoja de consolidacion.
        return ReporteObservacionExcel.generar(jdbc.query(SELECT + c.where() + " order by r.numero limit ?", FILA,
                args.toArray()));
    }

    @Transactional
    public ReporteObservacionDto.Fila revisar(UUID id, String estado, String respuesta, UsuarioPrincipal p) {
        exigirAdmin(p);
        String nuevo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(nuevo)) {
            throw ApiException.badRequest("Estado no válido.");
        }
        Map<String, Object> antes = jdbc.query("select status, response from reporte_observacion where id = ?", rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("estado", rs.getString(1));
            m.put("respuesta", rs.getString(2));
            return m;
        }, id);
        if (antes == null) {
            throw ApiException.notFound("Reporte no encontrado.");
        }
        String texto = limpio(respuesta, 1_000);
        jdbc.update("""
                update reporte_observacion set status = ?, response = ?, reviewed_by = ?, reviewed_at = now()
                where id = ?""", nuevo, texto, p.userId(), id);
        Map<String, Object> despues = new HashMap<>();
        despues.put("estado", nuevo);
        despues.put("respuesta", texto);
        auditoria.registrar("REPORTE_OBSERVACION", id, "REVISAR", antes, despues, p.userId());
        return jdbc.queryForObject(SELECT + " where r.id = ?", FILA, id);
    }

    public Imagen captura(UUID id, UsuarioPrincipal p) {
        exigirAdmin(p);
        String clave = jdbc.query("select capture_key from reporte_observacion where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (clave == null) {
            throw ApiException.notFound("El reporte no tiene captura.");
        }
        return new Imagen(storage.leerPrivado(clave), clave.endsWith(".png") ? "image/png" : "image/jpeg");
    }

    // ---------------------------------------------------------------- consultas

    private static final String SELECT = """
            select r.id, r.numero, r.created_at, r.user_id, u.display_name, u.corporate_email,
              (select string_agg(distinct ro.code, ' · ' order by ro.code) from usuario_rol ur
                 join rol ro on ro.role_id = ur.role_id where ur.user_id = r.user_id),
              r.module, r.screen, r.route, r.zone, r.description, r.capture_key is not null, r.user_agent,
              r.viewport, r.status, r.response, rv.display_name, r.reviewed_at
            from reporte_observacion r
            join usuario u on u.user_id = r.user_id
            left join usuario rv on rv.user_id = r.reviewed_by""";

    private static final RowMapper<ReporteObservacionDto.Fila> FILA = (rs, i) -> new ReporteObservacionDto.Fila(
            rs.getObject(1, UUID.class), rs.getInt(2), rs.getTimestamp(3).toInstant(), rs.getObject(4, UUID.class),
            rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
            rs.getString(11), rs.getString(12), rs.getBoolean(13), rs.getString(14), rs.getString(15),
            rs.getString(16), rs.getString(17), rs.getString(18), instante(rs.getTimestamp(19)));

    private record Consulta(List<String> condiciones, List<Object> args) {
        String where() {
            return condiciones.isEmpty() ? "" : " where " + String.join(" and ", condiciones);
        }
    }

    private static Consulta consulta(String estado, String q) {
        List<String> condiciones = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        String e = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (ESTADOS.contains(e)) {
            condiciones.add("r.status = ?");
            args.add(e);
        } else if ("ABIERTOS".equals(e)) {
            condiciones.add("r.status in ('PENDIENTE', 'EN_REVISION')");
        }
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> partes = new ArrayList<>();
            for (String col : List.of("r.module", "r.screen", "r.zone", "r.description", "r.route", "u.display_name",
                    "r.numero::text")) {
                partes.add(col + " ilike ?");
                args.add(patron);
            }
            condiciones.add("(" + String.join(" or ", partes) + ")");
        }
        return new Consulta(condiciones, args);
    }

    private ReporteObservacionDto.Conteo conteo() {
        return jdbc.queryForObject("""
                select count(*) filter (where status = 'PENDIENTE'), count(*) filter (where status = 'EN_REVISION'),
                  count(*) filter (where status = 'RESUELTO'), count(*) filter (where status = 'DESCARTADO')
                from reporte_observacion""",
                (rs, i) -> new ReporteObservacionDto.Conteo(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4)));
    }

    // ---------------------------------------------------------------- utilidades

    private static void exigirAdmin(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.ADMIN)) {
            throw ApiException.forbidden("Solo un administrador puede revisar los reportes.");
        }
    }

    /** Tope por usuario y hora: un boton abierto a todos no debe llenar la tabla. */
    private void controlarFrecuencia(UUID userId) {
        long hora = System.currentTimeMillis() / 3_600_000;
        long[] ventana = envios.compute(userId, (k, v) -> v == null || v[0] != hora ? new long[]{hora, 0} : v);
        synchronized (ventana) {
            if (++ventana[1] > MAX_POR_HORA) {
                throw ApiException.badRequest("Enviaste muchos reportes en poco tiempo. Intenta en un rato.");
            }
        }
        if (envios.size() > 5_000) {
            envios.entrySet().removeIf(en -> en.getValue()[0] < hora);
        }
    }

    private static byte[] validarCaptura(MultipartFile file) {
        if (file.getContentType() == null || !TIPOS_CAPTURA.contains(file.getContentType())) {
            throw ApiException.badRequest("La captura debe ser PNG o JPG.");
        }
        if (file.getSize() > MAX_CAPTURA) {
            throw ApiException.badRequest("La captura supera los 5 MB.");
        }
        try {
            byte[] contenido = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(contenido)) == null) {
                throw ApiException.badRequest("La captura no es una imagen válida.");
            }
            return contenido;
        } catch (IOException ex) {
            throw ApiException.badRequest("No se pudo leer la captura.");
        }
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static java.time.Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
