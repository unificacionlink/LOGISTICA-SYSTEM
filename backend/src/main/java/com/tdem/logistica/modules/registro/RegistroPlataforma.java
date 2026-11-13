package com.tdem.logistica.modules.registro;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registro de la plataforma para el administrador (DEC-L060): actividad
 * (metadata de cada operacion que cambia datos y de cada ingreso) y errores
 * (servidor y navegador).
 *
 * <p>La BD esta lejos (~150 ms por ida y vuelta): las entradas se encolan en
 * memoria y se escriben por lotes cada pocos segundos, fuera de la peticion y
 * de su transaccion. Si la cola se llena (BD caida) se descartan las nuevas:
 * registrar nunca debe tumbar ni frenar la operacion.
 */
@Component
public class RegistroPlataforma {

    /** Atributo de la peticion: usuario cuando aun no hay sesion (el ingreso). */
    public static final String ATR_USUARIO = "registro.usuario";
    /** Atributo de la peticion: dato extra sin secretos (correo intentado, motivo del rechazo). */
    public static final String ATR_DETALLE = "registro.detalle";

    private static final Logger log = LoggerFactory.getLogger(RegistroPlataforma.class);
    private static final int MAX_COLA = 5_000;
    private static final Pattern UUID_EN_RUTA =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public record Actividad(Instant en, UUID usuario, String metodo, String ruta, String modulo, UUID entidad,
                            int status, int ms, String ip, String agente, String detalle) {
    }

    public record Error(Instant en, String origen, UUID usuario, String metodo, String ruta, Integer status,
                        String tipo, String mensaje, String stack, String agente) {
    }

    private final JdbcTemplate jdbc;
    private final int diasActividad;
    private final int diasErrores;
    private final ConcurrentLinkedQueue<Actividad> actividades = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Error> errores = new ConcurrentLinkedQueue<>();
    private final AtomicInteger enCola = new AtomicInteger();

    public RegistroPlataforma(JdbcTemplate jdbc,
                              @Value("${app.registro.dias-actividad:180}") int diasActividad,
                              @Value("${app.registro.dias-errores:90}") int diasErrores) {
        this.jdbc = jdbc;
        this.diasActividad = diasActividad;
        this.diasErrores = diasErrores;
    }

    // ---------------------------------------------------------------- entrada

    public void actividad(Actividad a) {
        if (enCola.incrementAndGet() > MAX_COLA) {
            enCola.decrementAndGet();
            return;
        }
        actividades.add(a);
    }

    public void error(Error e) {
        if (enCola.incrementAndGet() > MAX_COLA) {
            enCola.decrementAndGet();
            return;
        }
        errores.add(e);
    }

    /** Error inesperado del servidor, con la ruta y el usuario de la peticion. */
    public void errorServidor(HttpServletRequest request, UUID usuario, int status, Throwable t) {
        error(new Error(Instant.now(), "SERVIDOR", usuario, request.getMethod(), corto(request.getRequestURI(), 500),
                status, corto(t.getClass().getName(), 200), corto(t.getMessage(), 2_000), pila(t),
                corto(request.getHeader("User-Agent"), 300)));
    }

    // ---------------------------------------------------------------- escritura por lotes

    @Scheduled(fixedDelayString = "${app.registro.intervalo-ms:3000}")
    public void volcar() {
        List<Actividad> lote = new ArrayList<>();
        for (Actividad a; (a = actividades.poll()) != null; ) {
            lote.add(a);
        }
        List<Error> loteErrores = new ArrayList<>();
        for (Error e; (e = errores.poll()) != null; ) {
            loteErrores.add(e);
        }
        enCola.addAndGet(-(lote.size() + loteErrores.size()));
        try {
            if (!lote.isEmpty()) {
                jdbc.batchUpdate("""
                        insert into registro_actividad (created_at, user_id, method, path, module, entity_id, status,
                          duration_ms, ip, user_agent, detail)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", lote, lote.size(), (ps, a) -> {
                    ps.setTimestamp(1, Timestamp.from(a.en()));
                    ps.setObject(2, a.usuario());
                    ps.setString(3, a.metodo());
                    ps.setString(4, a.ruta());
                    ps.setString(5, a.modulo());
                    ps.setObject(6, a.entidad());
                    ps.setInt(7, a.status());
                    ps.setInt(8, a.ms());
                    ps.setString(9, a.ip());
                    ps.setString(10, a.agente());
                    ps.setString(11, a.detalle());
                });
            }
            if (!loteErrores.isEmpty()) {
                jdbc.batchUpdate("""
                        insert into registro_error (created_at, origin, user_id, method, path, status, error_type,
                          message, stack, user_agent)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", loteErrores, loteErrores.size(), (ps, e) -> {
                    ps.setTimestamp(1, Timestamp.from(e.en()));
                    ps.setString(2, e.origen());
                    ps.setObject(3, e.usuario());
                    ps.setString(4, e.metodo());
                    ps.setString(5, e.ruta());
                    ps.setObject(6, e.status());
                    ps.setString(7, e.tipo());
                    ps.setString(8, e.mensaje());
                    ps.setString(9, e.stack());
                    ps.setString(10, e.agente());
                });
            }
        } catch (RuntimeException ex) {
            // Sin reintento: el registro es informativo y no debe acumularse sin fin.
            log.warn("No se pudo escribir el registro de la plataforma ({} entradas)", lote.size() + loteErrores.size(), ex);
        }
    }

    @PreDestroy
    public void alApagar() {
        volcar();
    }

    /** Conserva 180 dias de actividad y 90 de errores (configurable). */
    @Scheduled(cron = "${app.registro.purga-cron:0 30 3 * * *}", zone = "America/Lima")
    public void purgar() {
        try {
            jdbc.update("delete from registro_actividad where created_at < now() - make_interval(days => ?)", diasActividad);
            jdbc.update("delete from registro_error where created_at < now() - make_interval(days => ?)", diasErrores);
        } catch (RuntimeException ex) {
            log.warn("No se pudo depurar el registro de la plataforma", ex);
        }
    }

    // ---------------------------------------------------------------- utilidades

    /** Modulo de la ruta: /api/compras/rfq/.. -> compras. */
    static String modulo(String ruta) {
        if (ruta == null || !ruta.startsWith("/api/")) {
            return null;
        }
        String resto = ruta.substring(5);
        int fin = resto.indexOf('/');
        return corto(fin < 0 ? resto : resto.substring(0, fin), 40);
    }

    /** Primer identificador de la ruta: la entidad sobre la que se actuo. */
    static UUID entidad(String ruta) {
        Matcher m = UUID_EN_RUTA.matcher(ruta == null ? "" : ruta);
        return m.find() ? UUID.fromString(m.group()) : null;
    }

    static String corto(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String pila(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return corto(sw.toString(), 8_000);
    }
}
