package com.tdem.logistica.modules.repositorio;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.modules.repositorio.dto.DocumentoRepositorioDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Repositorio de documentos (DEC-L091, observacion 9 de Logistica): en una
 * consulta, los PDF que archiva el ERP y los archivos que ya se suben (pagos,
 * facturas, guias, comprobantes de caja chica y documentos de proveedores, estos
 * con su vencimiento), filtrables por tipo, empresa y mes. Los PDF archivados del
 * filtro se bajan juntos en un ZIP ordenado por tipo/empresa/mes.
 */
@Service
public class RepositorioService {

    private static final int MAXIMO = 500;
    private static final int MAXIMO_ZIP = 300;
    private static final Pattern PERIODO = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9_]{2,30}");

    private static final String SQL = """
            select * from (
              select d.doc_type as tipo, d.doc_code as codigo,
                     case d.version_key when 'ANULADA' then 'Anulada' when 'EMITIDA' then 'Emitida' else 'Aprobado' end
                       as detalle,
                     e.code as empresa, d.period as periodo, d.archived_at as fecha, cast(null as date) as vence,
                     '/api/repositorio/' || d.archive_id || '/archivo' as descarga, cast(null as text) as url,
                     case d.doc_type when 'REQUERIMIENTO' then '/requerimientos/' || d.entity_id
                                     when 'ORDEN_COMPRA' then '/compras/ordenes/' || d.entity_id
                                     else '/almacen/notas/' || d.entity_id end as ruta
                from repositorio_documento d join empresa e on e.company_id = d.company_id
              union all
              select case doc.document_type when 'FACTURA_PDF' then 'FACTURA' else 'CONSTANCIA_PAGO' end,
                     case doc.document_type when 'FACTURA_PDF' then coalesce(o.invoice_number, doc.file_name)
                                            else doc.file_name end,
                     'OC ' || o.order_code, e.code,
                     to_char(doc.uploaded_at at time zone 'America/Lima', 'YYYY-MM'), doc.uploaded_at, null,
                     null, doc.file_url, '/compras/ordenes/' || o.order_id
                from documento_relacion r
                join documento doc on doc.document_id = r.document_id
                join orden_abastecimiento o on o.order_id = r.entity_id
                join empresa e on e.company_id = o.company_id
               where r.entity_type = 'ORDEN'
              union all
              select 'GUIA', g.series || '-' || g.number, 'OC ' || o.order_code, e.code,
                     to_char(g.issue_date, 'YYYY-MM'), cast(g.issue_date as timestamptz), null,
                     null, dg.file_url, '/almacen/ordenes/' || o.order_id || '/recepcion'
                from guia_remision g
                join orden_abastecimiento o on o.order_id = g.order_id
                join empresa e on e.company_id = o.company_id
                left join documento dg on dg.document_id = g.document_id
              union all
              select 'COMPROBANTE_CAJA_CHICA', cc.petty_cash_code,
                     coalesce(cc.document_type || ' ' || coalesce(cc.document_series || '-', '') || cc.document_number, ''),
                     e.code, to_char(cc.purchase_date, 'YYYY-MM'), cc.created_at, null,
                     null, dc.file_url, '/requerimientos/' || cc.request_id || '/caja-chica'
                from caja_chica_ref cc
                join empresa e on e.company_id = cc.company_id
                join documento dc on dc.document_id = cc.document_id
              union all
              -- DEC-L092: factura de la caja chica como modulo (archivo o enlace).
              select 'COMPROBANTE_CAJA_CHICA', k.purchase_code,
                     k.document_type || ' ' || coalesce(k.document_series || '-', '') || k.document_number
                       || ' · ' || k.supplier_name || case when k.status = 'ANULADA' then ' (anulada)' else '' end,
                     e.code, to_char(k.purchase_date, 'YYYY-MM'), k.created_at, null,
                     null, coalesce(dk.file_url, k.evidence_url), '/caja-chica?tab=consolidado'
                from caja_chica_compra k
                join empresa e on e.company_id = k.company_id
                left join documento dk on dk.document_id = k.evidence_document_id
              union all
              select 'DOC_PROVEEDOR', coalesce(rq.name, dp.file_name), p.legal_name, e.code,
                     to_char(coalesce(cast(pd.issued_at as timestamptz), pd.created_at) at time zone 'America/Lima', 'YYYY-MM'),
                     pd.created_at, pd.expires_at,
                     '/api/proveedores/documentos/' || pd.document_id || '/archivo', null,
                     '/proveedores/' || p.supplier_id
                from proveedor_documento pd
                join documento dp on dp.document_id = pd.document_id
                join proveedor p on p.supplier_id = pd.supplier_id
                left join empresa e on e.company_id = p.company_id
                left join requisito_documento_proveedor rq on rq.requirement_id = pd.requirement_id
               where pd.status <> 'ANULADO'
            ) x
            where (cast(:tipo as text) is null or x.tipo = cast(:tipo as text))
              and (cast(:empresa as text) is null or x.empresa = cast(:empresa as text))
              and (cast(:periodo as text) is null or x.periodo = cast(:periodo as text))
              and (cast(:texto as text) is null
                   or x.codigo ilike '%' || cast(:texto as text) || '%'
                   or x.detalle ilike '%' || cast(:texto as text) || '%')
            order by x.fecha desc nulls last
            limit :maximo""";

    private static final String SQL_ZIP = """
            select d.storage_key, d.doc_type, e.code, d.period, d.doc_code, d.version_key
              from repositorio_documento d join empresa e on e.company_id = d.company_id
             where (cast(:tipo as text) is null or d.doc_type = cast(:tipo as text))
               and (cast(:empresa as text) is null or e.code = cast(:empresa as text))
               and (cast(:periodo as text) is null or d.period = cast(:periodo as text))
             order by d.period, d.doc_type, d.doc_code
             limit :maximo""";

    private final NamedParameterJdbcTemplate jdbc;
    private final StorageClient storage;

    public RepositorioService(NamedParameterJdbcTemplate jdbc, StorageClient storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    /** Logistica, Gerencia y Admin: los documentos son de las tres empresas. */
    private static void exigirAcceso(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN, Roles.GERENCIA)) {
            throw ApiException.forbidden("No tienes permiso para ver el repositorio de documentos.");
        }
    }

    public List<DocumentoRepositorioDto> listar(String tipo, String empresa, String periodo, String texto,
                                                UsuarioPrincipal principal) {
        exigirAcceso(principal);
        return jdbc.query(SQL, parametros(tipo, empresa, periodo, texto, MAXIMO), (rs, i) -> {
            Timestamp fecha = rs.getTimestamp("fecha");
            java.sql.Date vence = rs.getDate("vence");
            String url = rs.getString("url");
            return new DocumentoRepositorioDto(rs.getString("tipo"), rs.getString("codigo"), rs.getString("detalle"),
                    rs.getString("empresa"), rs.getString("periodo"), fecha != null ? fecha.toInstant() : null,
                    vence != null ? vence.toLocalDate() : null, rs.getString("descarga"),
                    url != null && url.startsWith("http") ? url : null, rs.getString("ruta"));
        });
    }

    public record Archivo(String nombre, byte[] contenido) {
    }

    /** Un PDF archivado por el ERP (bucket privado). */
    public Archivo descargar(UUID archiveId, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        List<String[]> filas = jdbc.query(
                "select storage_key, doc_code, version_key from repositorio_documento where archive_id = :id",
                new MapSqlParameterSource("id", archiveId),
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)});
        if (filas.isEmpty()) {
            throw ApiException.notFound("Documento no encontrado.");
        }
        String[] f = filas.get(0);
        return new Archivo(f[1] + ("ANULADA".equals(f[2]) ? "-ANULADA" : "") + ".pdf", storage.leerPrivado(f[0]));
    }

    /** Los PDF archivados del filtro en un ZIP con carpetas tipo/empresa/mes (hasta {@value #MAXIMO_ZIP}). */
    public Archivo zip(String tipo, String empresa, String periodo, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        List<String[]> archivos = jdbc.query(SQL_ZIP, parametros(tipo, empresa, periodo, null, MAXIMO_ZIP),
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6)});
        if (archivos.isEmpty()) {
            throw ApiException.badRequest("No hay PDF archivados con ese filtro.");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            Set<String> usados = new HashSet<>();
            for (String[] a : archivos) {
                String nombre = a[1] + "/" + a[2] + "/" + a[3] + "/" + a[4]
                        + ("ANULADA".equals(a[5]) ? "-ANULADA" : "") + ".pdf";
                if (!usados.add(nombre)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(nombre));
                zip.write(storage.leerPrivado(a[0]));
                zip.closeEntry();
            }
            zip.finish();
            String etiqueta = String.join("_", List.of(periodo != null && !periodo.isBlank() ? periodo : "todo",
                    empresa != null && !empresa.isBlank() ? empresa : "todas",
                    tipo != null && !tipo.isBlank() ? tipo : "todos"));
            return new Archivo("REPOSITORIO_" + etiqueta + ".zip", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo armar el ZIP del repositorio.", e);
        }
    }

    private static MapSqlParameterSource parametros(String tipo, String empresa, String periodo, String texto,
                                                    int maximo) {
        String t = limpio(tipo);
        String e = limpio(empresa);
        String p = periodo == null || periodo.isBlank() ? null : periodo.trim();
        if ((t != null && !CODIGO.matcher(t).matches()) || (e != null && !CODIGO.matcher(e).matches())) {
            throw ApiException.badRequest("Filtro invalido.");
        }
        if (p != null && !PERIODO.matcher(p).matches()) {
            throw ApiException.badRequest("El mes va como AAAA-MM.");
        }
        String q = texto == null || texto.isBlank() ? null : texto.trim();
        return new MapSqlParameterSource()
                .addValue("tipo", t, Types.VARCHAR)
                .addValue("empresa", e, Types.VARCHAR)
                .addValue("periodo", p, Types.VARCHAR)
                .addValue("texto", q, Types.VARCHAR)
                .addValue("maximo", maximo);
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
