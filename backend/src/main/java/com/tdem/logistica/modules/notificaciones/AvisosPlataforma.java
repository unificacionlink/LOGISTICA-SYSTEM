package com.tdem.logistica.modules.notificaciones;

import com.tdem.logistica.modules.notificaciones.dto.BandejaNotificacionesDto;
import com.tdem.logistica.modules.notificaciones.dto.NotificacionDto;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Avisos al solicitante dentro de la plataforma (DEC-L076): cada cambio de
 * estado de su requerimiento queda en su campana y le llega en vivo si esta
 * conectado. Reemplazan los correos de esos eventos (DEC-L066 sigue para los
 * aprobadores).
 *
 * <p>Se escribe en la transaccion de la accion (si se revierte, no queda
 * aviso) y el envio en vivo sale despues del commit. Nunca se avisa a quien
 * hizo la accion: ya lo sabe.
 */
@Service
public class AvisosPlataforma {

    public static final String APROBADO = "REQ_APROBADO";
    public static final String OBSERVADO = "REQ_OBSERVADO";
    public static final String RECHAZADO = "REQ_RECHAZADO";
    public static final String PARCIAL = "REQ_PARCIAL";
    public static final String COMPLETO = "REQ_COMPLETO";
    public static final String DESPACHADO = "DESPACHO_ENVIADO";
    public static final String RECIBIDO = "RECEPCION_REGISTRADA";

    private static final int MAXIMO_EN_BANDEJA = 40;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final RealtimeNotifier realtime;

    public AvisosPlataforma(JdbcTemplate jdbc, RealtimeNotifier realtime) {
        this.jdbc = jdbc;
        this.realtime = realtime;
    }

    // ---------------------------------------------------------------- eventos

    /** Aprobado, observado o rechazado. */
    public void decision(UUID requestId, String decision, String motivo, UUID actor) {
        switch (decision) {
            case "APROBADO" -> avisar(requestId, APROBADO, "%s fue aprobado",
                    "Logística ya empezó a atenderlo.", actor);
            case "OBSERVADO" -> avisar(requestId, OBSERVADO, "%s fue observado",
                    motivo != null ? "Corrígelo y vuelve a enviarlo: " + motivo : "Corrígelo y vuelve a enviarlo.", actor);
            case "RECHAZADO" -> avisar(requestId, RECHAZADO, "%s fue rechazado", motivo, actor);
            default -> {
            }
        }
    }

    /** La atencion cambio por una entrega: parcial o completa. */
    public void atencion(UUID requestId, String nuevoEstado, UUID actor) {
        switch (nuevoEstado) {
            case "ATENCION_PARCIAL" -> avisar(requestId, PARCIAL, "%s: entrega parcial",
                    "Ya se entregó parte de lo que pediste.", actor);
            case "ATENCION_COMPLETA" -> avisar(requestId, COMPLETO, "%s fue atendido por completo",
                    "Se entregó todo lo que pediste.", actor);
            default -> {
            }
        }
    }

    public void despachado(UUID requestId, String notaCodigo, LocalDate llegadaEstimada, UUID actor) {
        String detalle = "Nota " + notaCodigo
                + (llegadaEstimada != null ? " · llegada estimada " + llegadaEstimada.format(FECHA) : "")
                + ". Al recibirlo, registra la recepción.";
        avisar(requestId, DESPACHADO, "%s: tu pedido salió del almacén", detalle, actor);
    }

    /** {@code estadoEnvio}: RECEPCIONADO, RECEPCION_PARCIAL o RECEPCIONADO_OBSERVADO (DEC-L063). */
    public void recepcion(UUID requestId, String notaCodigo, String estadoEnvio, UUID actor) {
        String estado = switch (estadoEnvio) {
            case "RECEPCION_PARCIAL" -> "recepción parcial";
            case "RECEPCIONADO_OBSERVADO" -> "recibido con observaciones";
            default -> "recibido completo";
        };
        avisar(requestId, RECIBIDO, "%s: se registró la recepción", "Nota " + notaCodigo + ": " + estado + ".", actor);
    }

    /** El proveedor entrego directo en el CECO y alguien registro la recepcion (DEC-L084). */
    public void entregaDirecta(UUID requestId, String ordenCodigo, String cecoCodigo, UUID actor) {
        avisar(requestId, RECIBIDO, "%s: el proveedor entregó en obra",
                "Orden " + ordenCodigo + ": recepción registrada en " + cecoCodigo + ".", actor);
    }

    // ---------------------------------------------------------------- bandeja del usuario

    @Transactional(readOnly = true)
    public BandejaNotificacionesDto bandeja(UsuarioPrincipal principal) {
        List<NotificacionDto> items = jdbc.query(SELECT + " where n.user_id = ? order by n.created_at desc limit ?",
                (rs, i) -> mapear(rs), principal.userId(), MAXIMO_EN_BANDEJA);
        Integer noLeidas = jdbc.queryForObject(
                "select count(*) from notificacion where user_id = ? and read_at is null", Integer.class, principal.userId());
        return new BandejaNotificacionesDto(noLeidas == null ? 0 : noLeidas, items);
    }

    /** Solo las propias: la de otro usuario no se toca (y no se revela que existe). */
    @Transactional
    public BandejaNotificacionesDto marcarLeida(UUID id, UsuarioPrincipal principal) {
        jdbc.update("update notificacion set read_at = now() where notification_id = ? and user_id = ? and read_at is null",
                id, principal.userId());
        return bandeja(principal);
    }

    @Transactional
    public BandejaNotificacionesDto marcarTodas(UsuarioPrincipal principal) {
        jdbc.update("update notificacion set read_at = now() where user_id = ? and read_at is null", principal.userId());
        return bandeja(principal);
    }

    // ---------------------------------------------------------------- utilidades

    private static final String SELECT = """
            select n.notification_id, n.request_id, r.request_code, n.kind, n.title, n.body, n.created_at,
                   n.read_at is not null as leida
              from notificacion n left join requerimiento r on r.request_id = n.request_id""";

    private static NotificacionDto mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NotificacionDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(), rs.getBoolean(8));
    }

    /** {@code titulo} lleva %s para el codigo del requerimiento. */
    private void avisar(UUID requestId, String tipo, String titulo, String detalle, UUID actor) {
        List<Object[]> req = jdbc.query("select requester_user_id, request_code from requerimiento where request_id = ?",
                (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)}, requestId);
        if (req.isEmpty() || req.get(0)[0] == null || req.get(0)[0].equals(actor)) {
            return;
        }
        UUID destinatario = (UUID) req.get(0)[0];
        String texto = recortar(String.format(titulo, req.get(0)[1]), 200);
        String cuerpo = detalle == null || detalle.isBlank() ? null : recortar(detalle.trim(), 500);
        UUID id = jdbc.queryForObject("""
                insert into notificacion (user_id, request_id, kind, title, body) values (?, ?, ?, ?, ?)
                returning notification_id""", UUID.class, destinatario, requestId, tipo, texto, cuerpo);
        NotificacionDto dto = jdbc.queryForObject(SELECT + " where n.notification_id = ?", (rs, i) -> mapear(rs), id);
        despuesDelCommit(() -> realtime.notificacion(destinatario, dto));
    }

    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
