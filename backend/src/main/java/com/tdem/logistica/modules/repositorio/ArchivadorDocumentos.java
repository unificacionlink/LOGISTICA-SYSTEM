package com.tdem.logistica.modules.repositorio;

import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.documentos.DocumentosService;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Archiva en el bucket privado cada PDF oficial que aun no tiene copia (DEC-L091):
 * requerimiento aprobado, OC aprobada (con firmas), Nota de Inventario emitida o
 * anulada y la verificacion F-LOG-08 de una OC (DEC-L102), en repositorio/&lt;TIPO&gt;/&lt;EMPRESA&gt;/&lt;AAAA-MM&gt;/. Corre cada pocos
 * minutos y recupera lo pendiente, asi que un fallo del almacenamiento no pierde
 * nada: se reintenta en la siguiente vuelta. El PDF se genera como lo veria quien
 * hizo la accion (su nombre va en el pie).
 */
@Component
public class ArchivadorDocumentos {

    private static final Logger log = LoggerFactory.getLogger(ArchivadorDocumentos.class);
    private static final int POR_VUELTA = 20;

    private static final String PENDIENTES = """
            select * from (
              select 'REQUERIMIENTO' as tipo, r.request_id as id, r.request_code as codigo, r.company_id, e.code as empresa,
                     to_char(a.decided_at at time zone 'America/Lima', 'YYYY-MM') as periodo, 'APROBADO' as version,
                     a.decided_by as actor, a.decided_at as fecha
                from requerimiento r
                join empresa e on e.company_id = r.company_id
                join aprobacion a on a.entity_type = 'REQUERIMIENTO' and a.entity_id = r.request_id
                                 and a.status = 'APROBADO' and a.decided_at is not null
              union all
              select 'ORDEN_COMPRA', o.order_id, o.order_code, o.company_id, e.code,
                     to_char(o.approved_at at time zone 'America/Lima', 'YYYY-MM'), 'APROBADA', o.approved_by, o.approved_at
                from orden_abastecimiento o
                join empresa e on e.company_id = o.company_id
               where o.approved_at is not null
              union all
              select 'NOTA_INVENTARIO', n.inventory_note_id, n.inventory_note_code, n.company_id, e.code,
                     to_char(n.issued_at at time zone 'America/Lima', 'YYYY-MM'),
                     case when n.status = 'ANULADA' then 'ANULADA' else 'EMITIDA' end,
                     coalesce(n.annulled_by, n.created_by), coalesce(n.annulled_at, n.issued_at)
                from nota_inventario n
                join empresa e on e.company_id = n.company_id
               where n.issued_at is not null
              union all
              select 'VERIFICACION_OC', v.order_id, 'F-LOG-08_' || o.order_code, o.company_id, e.code,
                     to_char(v.verified_at at time zone 'America/Lima', 'YYYY-MM'), 'VERIFICADA',
                     v.verified_by, v.verified_at
                from verificacion_oc v
                join orden_abastecimiento o on o.order_id = v.order_id
                join empresa e on e.company_id = o.company_id
            ) p
            where not exists (select 1 from repositorio_documento d
                               where d.doc_type = p.tipo and d.entity_id = p.id and d.version_key = p.version)
            order by p.fecha
            limit ?""";

    private record Pendiente(String tipo, UUID id, String codigo, UUID companyId, String empresa, String periodo,
                             String version, UUID actor) {
    }

    private final JdbcTemplate jdbc;
    private final DocumentosService documentos;
    private final StorageClient storage;
    private final ReentrantLock enCurso = new ReentrantLock();

    public ArchivadorDocumentos(JdbcTemplate jdbc, DocumentosService documentos, StorageClient storage) {
        this.jdbc = jdbc;
        this.documentos = documentos;
        this.storage = storage;
    }

    @Scheduled(fixedDelayString = "${app.repositorio.intervalo-ms:180000}",
            initialDelayString = "${app.repositorio.intervalo-ms:180000}")
    public void programado() {
        try {
            int n = archivarPendientes();
            if (n > 0) {
                log.info("Repositorio: {} documento(s) archivado(s).", n);
            }
        } catch (RuntimeException e) {
            log.warn("Repositorio: no se pudo archivar: {}", e.getMessage());
        }
    }

    /** Archiva hasta {@value #POR_VUELTA} pendientes; devuelve cuantos quedaron archivados. */
    public int archivarPendientes() {
        if (!storage.privadoConfigurado() || !enCurso.tryLock()) {
            return 0;
        }
        try {
            List<Pendiente> pendientes = jdbc.query(PENDIENTES, (rs, i) -> new Pendiente(
                    rs.getString("tipo"), rs.getObject("id", UUID.class), rs.getString("codigo"),
                    rs.getObject("company_id", UUID.class), rs.getString("empresa"), rs.getString("periodo"),
                    rs.getString("version"), rs.getObject("actor", UUID.class)), POR_VUELTA);
            int archivados = 0;
            for (Pendiente p : pendientes) {
                try {
                    archivar(p);
                    archivados++;
                } catch (RuntimeException e) {
                    log.warn("Repositorio: {} {} no se archivo: {}", p.tipo(), p.codigo(), e.getMessage());
                }
            }
            return archivados;
        } finally {
            enCurso.unlock();
        }
    }

    private void archivar(Pendiente p) {
        // Como lo veria quien hizo la accion (su nombre va en el pie); ADMIN para leer sin restriccion de ambito.
        UsuarioPrincipal como = new UsuarioPrincipal(p.actor() != null ? p.actor() : new UUID(0, 0), List.of(Roles.ADMIN));
        DocumentosService.Pdf pdf = switch (p.tipo()) {
            case "REQUERIMIENTO" -> documentos.requerimiento(p.id(), como);
            case "ORDEN_COMPRA" -> documentos.orden(p.id(), como);
            case "VERIFICACION_OC" -> documentos.verificacionOc(p.id(), como);
            default -> documentos.notaInventario(p.id(), como);
        };
        String clave = "repositorio/" + p.tipo() + "/" + p.empresa() + "/" + p.periodo() + "/"
                + p.codigo() + ("ANULADA".equals(p.version()) ? "-ANULADA" : "") + ".pdf";
        storage.subirPrivado(clave, pdf.contenido(), "application/pdf");
        jdbc.update("""
                insert into repositorio_documento (doc_type, entity_id, doc_code, company_id, period, version_key,
                                                   storage_key, size_bytes)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (doc_type, entity_id, version_key) do nothing""",
                p.tipo(), p.id(), p.codigo(), p.companyId(), p.periodo(), p.version(), clave, pdf.contenido().length);
    }
}
