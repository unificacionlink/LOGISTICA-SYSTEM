package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.proveedores.dto.DocumentosProveedorDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigirMotivo;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.limpio;
import static com.tdem.logistica.modules.proveedores.FichaProveedorService.fecha;

/**
 * Documentos del proveedor (DEC-L071): van al bucket PRIVADO y se descargan
 * solo por el backend, con sesion y rol Logistica/Admin. La BD guarda la
 * clave ("privado:...") en documento.file_url. Se anulan con motivo; nunca se
 * borran (ni la fila ni el archivo).
 */
@Service
@Transactional(readOnly = true)
public class DocumentoProveedorService {

    /** Archivo descargable: contenido, nombre y tipo. */
    public record Archivo(byte[] contenido, String nombre, String tipo) {
    }

    public static final String PREFIJO_PRIVADO = "privado:";
    private static final long TOPE_BYTES = 8L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONES = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final JdbcTemplate jdbc;
    private final StorageClient storage;
    private final ProveedorService proveedores;
    private final FichaProveedorService ficha;
    private final Auditoria auditoria;

    public DocumentoProveedorService(JdbcTemplate jdbc, StorageClient storage, ProveedorService proveedores,
                                     FichaProveedorService ficha, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.proveedores = proveedores;
        this.ficha = ficha;
        this.auditoria = auditoria;
    }

    public DocumentosProveedorDto listar(UUID supplierId, UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(supplierId);
        LocalDate hoy = VigenciaEvaluacion.hoy();
        int aviso = ficha.diasAviso();
        List<DocumentosProveedorDto.Documento> documentos = jdbc.query("""
                select d.supplier_document_id, doc.file_name, doc.mime_type, d.requirement_id, r.name, d.evaluation_id,
                       d.issued_at, d.expires_at, d.notes, d.status, d.void_reason, u.display_name, d.created_at
                  from proveedor_documento d
                  join documento doc on doc.document_id = d.document_id
                  left join requisito_documento_proveedor r on r.requirement_id = d.requirement_id
                  left join usuario u on u.user_id = d.created_by
                 where d.supplier_id = ?
                 order by d.status, d.created_at desc""",
                (rs, n) -> {
                    LocalDate vence = fecha(rs, "expires_at");
                    Timestamp creado = rs.getTimestamp("created_at");
                    return new DocumentosProveedorDto.Documento(rs.getObject(1, UUID.class), rs.getString(2),
                            rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getObject(6, UUID.class),
                            fecha(rs, "issued_at"), vence, rs.getString("notes"), rs.getString("status"),
                            rs.getString("void_reason"), rs.getString("display_name"),
                            creado.toInstant().atOffset(ZoneOffset.UTC),
                            vence == null ? VigenciaEvaluacion.VIGENTE : VigenciaEvaluacion.estado(vence, hoy, aviso));
                }, supplierId);

        List<DocumentosProveedorDto.Requisito> requisitos = jdbc.query("""
                select requirement_id, name, description, required, has_expiry
                  from requisito_documento_proveedor where active order by sort_order, name""",
                (rs, n) -> {
                    UUID requirementId = rs.getObject(1, UUID.class);
                    // El documento activo que vence mas tarde (o el que no vence) es el que cuenta.
                    DocumentosProveedorDto.Documento vigente = documentos.stream()
                            .filter(d -> "ACTIVO".equals(d.status()) && requirementId.equals(d.requirementId()))
                            .max((a, b) -> {
                                LocalDate x = a.expiresAt() != null ? a.expiresAt() : LocalDate.MAX;
                                LocalDate y = b.expiresAt() != null ? b.expiresAt() : LocalDate.MAX;
                                return x.compareTo(y);
                            }).orElse(null);
                    String estado = vigente == null ? "FALTA" : vigente.vigencia();
                    return new DocumentosProveedorDto.Requisito(requirementId, rs.getString(2), rs.getString(3),
                            rs.getBoolean(4), rs.getBoolean(5), estado,
                            vigente != null ? vigente.supplierDocumentId() : null,
                            vigente != null ? vigente.expiresAt() : null);
                });
        return new DocumentosProveedorDto(requisitos, documentos);
    }

    @Transactional
    public DocumentosProveedorDto subir(UUID supplierId, MultipartFile file, UUID requirementId, UUID evaluationId,
                                        LocalDate issuedAt, LocalDate expiresAt, String notes,
                                        UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(supplierId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        if (file.getSize() > TOPE_BYTES) {
            throw ApiException.badRequest("El archivo supera los 8 MB.");
        }
        String extension = file.getContentType() != null ? EXTENSIONES.get(file.getContentType()) : null;
        if (extension == null) {
            throw ApiException.badRequest("Formato no soportado (usa PDF, JPG, PNG o WEBP).");
        }
        if (requirementId != null) {
            List<Boolean> vence = jdbc.queryForList(
                    "select has_expiry from requisito_documento_proveedor where requirement_id = ? and active",
                    Boolean.class, requirementId);
            if (vence.isEmpty()) {
                throw ApiException.badRequest("El requisito no existe o está inactivo.");
            }
            if (vence.get(0) && expiresAt == null) {
                throw ApiException.badRequest("Este documento tiene vencimiento: indica la fecha.");
            }
        }
        if (evaluationId != null) {
            Integer propia = jdbc.queryForObject(
                    "select count(*) from evaluacion_proveedor where evaluation_id = ? and supplier_id = ?",
                    Integer.class, evaluationId, supplierId);
            if (propia == null || propia == 0) {
                throw ApiException.badRequest("La evaluación no es de este proveedor.");
            }
        }
        if (issuedAt != null && expiresAt != null && expiresAt.isBefore(issuedAt)) {
            throw ApiException.badRequest("El vencimiento no puede ser anterior a la emisión.");
        }

        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String clave = storage.subirPrivado("proveedores/" + supplierId + "/" + UUID.randomUUID() + extension,
                contenido, file.getContentType());
        UUID documentId = UUID.randomUUID();
        String nombre = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename() : "documento" + extension;
        jdbc.update("""
                insert into documento (document_id, document_type, file_name, file_url, mime_type, file_hash, uploaded_by)
                values (?, 'DOC_PROVEEDOR', ?, ?, ?, ?, ?)""",
                documentId, recortar(nombre, 220), PREFIJO_PRIVADO + clave, file.getContentType(), sha256(contenido),
                principal.userId());
        UUID supplierDocumentId = UUID.randomUUID();
        jdbc.update("""
                insert into proveedor_documento (supplier_document_id, supplier_id, document_id, requirement_id,
                       evaluation_id, issued_at, expires_at, notes, created_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                supplierDocumentId, supplierId, documentId, requirementId, evaluationId, issuedAt, expiresAt,
                limpio(notes), principal.userId());
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("documento", nombre);
        despues.put("requisito", requirementId != null ? requirementId.toString() : null);
        despues.put("vence", expiresAt != null ? expiresAt.toString() : null);
        despues.values().removeIf(v -> v == null);
        auditoria.registrar("PROVEEDOR", supplierId, "SUBIR_DOCUMENTO", null, despues, principal.userId());
        return listar(supplierId, principal);
    }

    /** Contenido del archivo, leido del bucket privado por el backend. */
    public Archivo descargar(UUID supplierDocumentId, UsuarioPrincipal principal) {
        exigir(principal);
        List<Map<String, Object>> fila = jdbc.queryForList("""
                select doc.file_url, doc.file_name, doc.mime_type
                  from proveedor_documento d join documento doc on doc.document_id = d.document_id
                 where d.supplier_document_id = ?""", supplierDocumentId);
        if (fila.isEmpty()) {
            throw ApiException.notFound("Documento no encontrado.");
        }
        String url = String.valueOf(fila.get(0).get("file_url"));
        if (!url.startsWith(PREFIJO_PRIVADO)) {
            throw ApiException.badRequest("El documento no está en el almacenamiento privado.");
        }
        byte[] contenido = storage.leerPrivado(url.substring(PREFIJO_PRIVADO.length()));
        Object tipo = fila.get(0).get("mime_type");
        return new Archivo(contenido, String.valueOf(fila.get(0).get("file_name")),
                tipo != null ? tipo.toString() : "application/octet-stream");
    }

    @Transactional
    public DocumentosProveedorDto anular(UUID supplierDocumentId, String motivo, UsuarioPrincipal principal) {
        exigir(principal);
        String m = exigirMotivo(motivo, "Escribe el motivo de la anulación.");
        List<Map<String, Object>> fila = jdbc.queryForList("""
                select d.supplier_id, d.status, doc.file_name
                  from proveedor_documento d join documento doc on doc.document_id = d.document_id
                 where d.supplier_document_id = ?""", supplierDocumentId);
        if (fila.isEmpty()) {
            throw ApiException.notFound("Documento no encontrado.");
        }
        if (!"ACTIVO".equals(fila.get(0).get("status"))) {
            throw ApiException.badRequest("El documento ya está anulado.");
        }
        UUID supplierId = (UUID) fila.get(0).get("supplier_id");
        jdbc.update("""
                update proveedor_documento set status = 'ANULADO', void_reason = ?, voided_by = ?, voided_at = now()
                 where supplier_document_id = ?""", m, principal.userId(), supplierDocumentId);
        auditoria.registrar("PROVEEDOR", supplierId, "ANULAR_DOCUMENTO",
                Map.of("documento", String.valueOf(fila.get(0).get("file_name"))), Map.of("motivo", m),
                principal.userId());
        return listar(supplierId, principal);
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
