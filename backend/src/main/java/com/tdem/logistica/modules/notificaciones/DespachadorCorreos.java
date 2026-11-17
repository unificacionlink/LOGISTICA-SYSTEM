package com.tdem.logistica.modules.notificaciones;

import com.tdem.logistica.modules.notificaciones.EnvioCorreo.Mensaje;
import com.tdem.logistica.modules.registro.RegistroPlataforma;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Envia la bandeja de salida (DEC-L066) fuera de las peticiones. Toma un lote
 * con {@code skip locked} y lo aparta unos minutos (si la instancia se cae, otra
 * vuelta lo retoma). Un fallo se reintenta a los 1, 5, 15 y 60 minutos; al
 * quinto queda en ERROR y va al registro de errores del ADMIN (RF-063).
 */
@Component
public class DespachadorCorreos {

    static final int MAX_INTENTOS = 5;
    private static final int LOTE = 20;
    private static final int[] ESPERA_MINUTOS = {1, 5, 15, 60};
    private static final Logger log = LoggerFactory.getLogger(DespachadorCorreos.class);

    private final JdbcTemplate jdbc;
    private final EnvioCorreo envio;
    private final RegistroPlataforma registro;
    private final ReentrantLock enCurso = new ReentrantLock();

    public DespachadorCorreos(JdbcTemplate jdbc, EnvioCorreo envio, RegistroPlataforma registro) {
        this.jdbc = jdbc;
        this.envio = envio;
        this.registro = registro;
    }

    private record Pendiente(UUID id, String evento, String para, String asunto, String html, int intentos) {
    }

    @Scheduled(fixedDelayString = "${app.correo.intervalo-ms:30000}",
            initialDelayString = "${app.correo.intervalo-ms:30000}")
    public void programado() {
        try {
            enviarPendientes();
        } catch (RuntimeException e) {
            log.warn("Correos: no se pudo procesar la bandeja de salida: {}", e.getMessage());
        }
    }

    /** Envia lo pendiente; devuelve cuantos salieron. */
    public int enviarPendientes() {
        if (!enCurso.tryLock()) {
            return 0;
        }
        try {
            if (!envio.configurado()) {
                jdbc.update("""
                        update correo_saliente set status = 'OMITIDO',
                               last_error = 'El correo no esta configurado (RESEND_API_KEY).'
                         where status = 'PENDIENTE'""");
                return 0;
            }
            int enviados = 0;
            List<Pendiente> lote;
            while (!(lote = apartar()).isEmpty()) {
                for (Pendiente p : lote) {
                    if (enviar(p)) {
                        enviados++;
                    }
                }
                if (lote.size() < LOTE) {
                    break;
                }
            }
            return enviados;
        } finally {
            enCurso.unlock();
        }
    }

    /** Aparta el lote 10 minutos: mientras se envia, ninguna otra vuelta lo toma. */
    private List<Pendiente> apartar() {
        return jdbc.query("""
                update correo_saliente set next_attempt_at = now() + interval '10 minutes'
                 where correo_id in (
                       select correo_id from correo_saliente
                        where status = 'PENDIENTE' and next_attempt_at <= now()
                        order by created_at
                        limit ?
                          for update skip locked)
                returning correo_id, event_type, to_email, subject, html, attempts""",
                (rs, n) -> new Pendiente(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getInt(6)),
                LOTE);
    }

    private boolean enviar(Pendiente p) {
        try {
            String idProveedor = envio.enviar(new Mensaje(p.id(), p.para(), p.asunto(), p.html()));
            jdbc.update("""
                    update correo_saliente set status = 'ENVIADO', sent_at = now(), attempts = attempts + 1,
                           provider_message_id = ?, last_error = null
                     where correo_id = ?""", idProveedor, p.id());
            return true;
        } catch (RuntimeException e) {
            fallo(p, e.getMessage());
            return false;
        }
    }

    private void fallo(Pendiente p, String error) {
        int intentos = p.intentos() + 1;
        if (intentos >= MAX_INTENTOS) {
            jdbc.update("""
                    update correo_saliente set status = 'ERROR', attempts = ?, last_error = ?
                     where correo_id = ?""", intentos, error, p.id());
            registro.error(new RegistroPlataforma.Error(Instant.now(), "SERVIDOR", null, null,
                    "correo/" + p.evento(), null, "CorreoNoEnviado",
                    "No se pudo enviar \"" + p.asunto() + "\" a " + p.para() + " tras " + intentos
                            + " intentos: " + error, null, null));
            log.warn("Correo {} sin enviar tras {} intentos: {}", p.id(), intentos, error);
            return;
        }
        int espera = ESPERA_MINUTOS[Math.min(intentos - 1, ESPERA_MINUTOS.length - 1)];
        jdbc.update("""
                update correo_saliente set attempts = ?, last_error = ?,
                       next_attempt_at = now() + make_interval(mins => ?)
                 where correo_id = ?""", intentos, error, espera, p.id());
    }
}
