package com.tdem.logistica.modules.notificaciones;

import com.tdem.logistica.modules.requerimientos.SlaAtencion;
import com.tdem.logistica.config.UrlPublica;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Unico punto que encola correos (DEC-L066), como {@code RealtimeNotifier} lo es
 * del WebSocket. Escribe en {@code correo_saliente} dentro de la transaccion de
 * la accion: si la accion se revierte, no sale correo. {@link DespachadorCorreos}
 * los envia despues.
 *
 * <p>Destinatarios: solo cuentas activas con acceso, y nunca quien hizo la accion.
 */
@Component
public class AvisosCorreo {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> MODALIDAD = Map.of(
            "AGENCIA", "Agencia", "PROVEEDOR_ENTREGA", "Entrega del proveedor", "RECOJO", "Recojo",
            "TERRESTRE_NORMAL", "Terrestre", "COURIER", "Courier", "PROPIO", "Movilidad propia");

    private final JdbcTemplate jdbc;
    private final UrlPublica url;

    public AvisosCorreo(JdbcTemplate jdbc, UrlPublica url) {
        this.jdbc = jdbc;
        this.url = url;
    }

    private record Req(UUID id, String codigo, UUID cecoId, String ceco, String prioridad, String justificacion,
                       LocalDate fechaRequerida, UUID solicitanteId) {
    }

    private record Destinatario(UUID userId, String correo, String nombre) {
    }

    // ---------------------------------------------------------------- aprobacion

    /** REQ enviado: a quien puede decidirlo (regla de AmbitoAprobacion, sin ADMIN, que es respaldo). */
    public void aprobacionPendiente(UUID requestId, UUID asignado, UUID actor) {
        Req r = req(requestId);
        if (r == null) {
            return;
        }
        for (Destinatario d : aprobadores(r, asignado)) {
            if (!d.userId().equals(actor)) {
                encolar("APROBACION_PENDIENTE", r.id(), d, "Requerimiento por aprobar: " + r.codigo(),
                        correoAprobacion(r, d, "Tienes un requerimiento pendiente de aprobación."), null);
            }
        }
    }

    /** Recordatorio diario (8:00) de lo que sigue pendiente; una vez por dia y persona. */
    public void recordatorioAprobacion(UUID approvalId, UUID requestId, UUID asignado, LocalDate hoy) {
        Req r = req(requestId);
        if (r == null) {
            return;
        }
        for (Destinatario d : aprobadores(r, asignado)) {
            encolar("RECORDATORIO_APROBACION", r.id(), d, "Recordatorio: " + r.codigo() + " sigue por aprobar",
                    correoAprobacion(r, d, "Este requerimiento sigue pendiente de aprobación."),
                    "RECORDATORIO|" + approvalId + "|" + d.userId() + "|" + hoy);
        }
    }

    private String correoAprobacion(Req r, Destinatario d, String parrafo) {
        Map<String, String> datos = datosReq(r);
        datos.put("Solicitante", nombre(r.solicitanteId()));
        datos.put("Justificación", r.justificacion());
        return PlantillasCorreo.correo("Requerimiento por aprobar", d.nombre(), parrafo, datos,
                url.de("/aprobaciones"), "Revisar en la plataforma");
    }

    // ---------------------------------------------------------------- decision

    /** Aprobado, observado (RF-007) o rechazado: al solicitante. */
    public void decision(UUID requestId, String decision, String motivo, UUID actor) {
        Req r = req(requestId);
        Destinatario d = r != null ? solicitante(r, actor) : null;
        if (d == null) {
            return;
        }
        String evento;
        String titulo;
        String parrafo;
        switch (decision) {
            case "APROBADO" -> {
                evento = "REQ_APROBADO";
                titulo = "Requerimiento aprobado";
                parrafo = "Tu requerimiento fue aprobado. Logística ya empezó a atenderlo.";
            }
            case "OBSERVADO" -> {
                evento = "REQ_OBSERVADO";
                titulo = "Requerimiento observado";
                parrafo = "Tu requerimiento fue observado. Revisa la observación, corrígelo y vuelve a enviarlo.";
            }
            case "RECHAZADO" -> {
                evento = "REQ_RECHAZADO";
                titulo = "Requerimiento rechazado";
                parrafo = "Tu requerimiento fue rechazado.";
            }
            default -> {
                return;
            }
        }
        Map<String, String> datos = datosReq(r);
        datos.put("Decidió", nombre(actor));
        datos.put("Motivo", motivo);
        encolar(evento, r.id(), d, titulo + ": " + r.codigo(),
                PlantillasCorreo.correo(titulo, d.nombre(), parrafo, datos,
                        url.de("/requerimientos/" + r.id()), "Ver requerimiento"), null);
    }

    // ---------------------------------------------------------------- despacho y recepcion

    public void despachoEnviado(UUID requestId, String notaCodigo, String modalidad, String transportista,
                                String seguimiento, LocalDate llegadaEstimada, UUID actor) {
        Req r = req(requestId);
        Destinatario d = r != null ? solicitante(r, actor) : null;
        if (d == null) {
            return;
        }
        Map<String, String> datos = datosReq(r);
        datos.put("Nota de inventario", notaCodigo);
        datos.put("Modalidad", MODALIDAD.getOrDefault(modalidad, modalidad));
        datos.put("Transportista", transportista);
        datos.put("Seguimiento", seguimiento);
        datos.put("Llegada estimada", llegadaEstimada != null ? llegadaEstimada.format(FECHA) : null);
        encolar("DESPACHO_ENVIADO", r.id(), d, "Tu pedido salió del almacén: " + r.codigo(),
                PlantillasCorreo.correo("Pedido despachado", d.nombre(),
                        "Tu pedido salió del almacén central. Al recibirlo, registra la recepción en la plataforma.",
                        datos, url.de("/requerimientos/" + r.id()), "Ver requerimiento"), null);
    }

    /** {@code estadoEnvio}: RECEPCIONADO, RECEPCION_PARCIAL o RECEPCIONADO_OBSERVADO (DEC-L063). */
    public void recepcionRegistrada(UUID requestId, String notaCodigo, String resultado, String estadoEnvio,
                                    String observacion, UUID actor) {
        Req r = req(requestId);
        Destinatario d = r != null ? solicitante(r, actor) : null;
        if (d == null) {
            return;
        }
        Map<String, String> datos = datosReq(r);
        datos.put("Nota de inventario", notaCodigo);
        datos.put("Resultado", "CONFORME".equals(resultado) ? "Conforme" : "No conforme");
        datos.put("Estado", switch (estadoEnvio) {
            case "RECEPCION_PARCIAL" -> "Recepción parcial";
            case "RECEPCIONADO_OBSERVADO" -> "Recibido con observaciones";
            default -> "Recibido completo";
        });
        datos.put("Registró", nombre(actor));
        datos.put("Observación", observacion);
        encolar("RECEPCION_REGISTRADA", r.id(), d, "Recepción registrada: " + r.codigo(),
                PlantillasCorreo.correo("Recepción en destino", d.nombre(),
                        "Se registró la recepción de tu pedido en destino.",
                        datos, url.de("/requerimientos/" + r.id()), "Ver requerimiento"), null);
    }

    // ---------------------------------------------------------------- utilidades

    private void encolar(String evento, UUID requestId, Destinatario d, String asunto, String html, String dedupe) {
        jdbc.update("""
                insert into correo_saliente (event_type, entity_type, entity_id, recipient_user_id, to_email,
                                             subject, html, dedupe_key)
                values (?, 'REQUERIMIENTO', ?, ?, ?, ?, ?, ?)
                on conflict (dedupe_key) do nothing""",
                evento, requestId, d.userId(), d.correo(), recortar(asunto, 300), html, dedupe);
    }

    private Req req(UUID requestId) {
        return jdbc.query("""
                select r.request_id, r.request_code, r.ceco_id, coalesce(c.code || ' — ', '') || c.name,
                       r.priority, r.justification, r.required_date, r.requester_user_id
                  from requerimiento r
                  join ceco c on c.ceco_id = r.ceco_id
                 where r.request_id = ?""",
                (rs, n) -> new Req(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, LocalDate.class),
                        rs.getObject(8, UUID.class)),
                requestId).stream().findFirst().orElse(null);
    }

    /**
     * Con aprobador asignado: ese usuario y la GERENCIA que tenga el CECO.
     * Sin asignado: todo APROBADOR y GERENCIA. Nunca el solicitante.
     */
    private List<Destinatario> aprobadores(Req r, UUID asignado) {
        return jdbc.query("""
                select distinct u.user_id, u.corporate_email, u.display_name
                  from usuario u
                  join usuario_rol ur on ur.user_id = u.user_id
                  join rol ro on ro.role_id = ur.role_id and ro.active
                 where u.active and u.password_hash is not null
                   and ro.code in ('APROBADOR', 'GERENCIA')
                   and (cast(? as uuid) is null
                        or u.user_id = cast(? as uuid)
                        or (ro.code = 'GERENCIA' and exists (
                              select 1 from usuario_ceco uc where uc.user_id = u.user_id and uc.ceco_id = ?)))
                 order by u.display_name""",
                (rs, n) -> new Destinatario(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                asignado, asignado, r.cecoId()).stream()
                .filter(d -> !d.userId().equals(r.solicitanteId()))
                .toList();
    }

    /** El solicitante, si tiene acceso y no es quien hizo la accion. */
    private Destinatario solicitante(Req r, UUID actor) {
        if (r.solicitanteId() == null || Objects.equals(r.solicitanteId(), actor)) {
            return null;
        }
        return jdbc.query("""
                select user_id, corporate_email, display_name from usuario
                 where user_id = ? and active and password_hash is not null""",
                (rs, n) -> new Destinatario(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                r.solicitanteId()).stream().findFirst().orElse(null);
    }

    private String nombre(UUID userId) {
        if (userId == null) {
            return null;
        }
        return jdbc.query("select display_name from usuario where user_id = ?", (rs, n) -> rs.getString(1), userId)
                .stream().findFirst().orElse(null);
    }

    private static Map<String, String> datosReq(Req r) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Requerimiento", r.codigo());
        datos.put("CECO", r.ceco());
        datos.put("Prioridad", SlaAtencion.etiqueta(r.prioridad()));
        datos.put("Fecha requerida", r.fechaRequerida() != null ? r.fechaRequerida().format(FECHA) : null);
        return datos;
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
