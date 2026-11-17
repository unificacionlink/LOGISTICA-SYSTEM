package com.tdem.logistica.modules.notificaciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Recordatorio diario (DEC-L066): a las 8:00, lunes a sabado, cada requerimiento
 * que sigue por aprobar desde antes de hoy se vuelve a avisar a quien puede
 * decidirlo. Una sola vez por dia y persona (dedupe_key).
 */
@Component
public class RecordatorioAprobaciones {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Logger log = LoggerFactory.getLogger(RecordatorioAprobaciones.class);

    private final JdbcTemplate jdbc;
    private final AvisosCorreo avisos;

    public RecordatorioAprobaciones(JdbcTemplate jdbc, AvisosCorreo avisos) {
        this.jdbc = jdbc;
        this.avisos = avisos;
    }

    private record Pendiente(UUID approvalId, UUID requestId, UUID asignado) {
    }

    @Scheduled(cron = "${app.correo.recordatorio-cron:0 0 8 * * MON-SAT}", zone = "America/Lima")
    public void programado() {
        try {
            int n = recordar(LocalDate.now(LIMA));
            log.info("Recordatorio de aprobaciones: {} requerimiento(s) pendiente(s).", n);
        } catch (RuntimeException e) {
            log.warn("Recordatorio de aprobaciones fallo: {}", e.getMessage());
        }
    }

    /** Encola los recordatorios del dia; devuelve cuantos requerimientos siguen pendientes. */
    @Transactional
    public int recordar(LocalDate hoy) {
        List<Pendiente> pendientes = jdbc.query("""
                select a.approval_id, a.entity_id, a.assigned_to
                  from aprobacion a
                  join requerimiento r on r.request_id = a.entity_id
                 where a.entity_type = 'REQUERIMIENTO' and a.status = 'PENDIENTE'
                   and r.status = 'PENDIENTE_APROBACION'
                   and a.created_at < ?""",
                (rs, n) -> new Pendiente(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class)),
                Timestamp.from(hoy.atStartOfDay(LIMA).toInstant()));
        for (Pendiente p : pendientes) {
            avisos.recordatorioAprobacion(p.approvalId(), p.requestId(), p.asignado(), hoy);
        }
        return pendientes.size();
    }
}
