package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Documento;
import com.tdem.logistica.entity.NotaInventario;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.repository.NotaInventarioRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.tdem.logistica.modules.almacen.RecepcionService.exigirLogisticaOAdmin;

/**
 * Preparacion de la Nota de Inventario antes del envio (Lote 18, ALM-02 de la
 * V38.7, DEC-L062): picking confirmado y embalaje con bultos y evidencia. Son
 * fechas sobre la NI, que sigue EMITIDA hasta que se registra el envio.
 */
@Service
public class PreparacionNotaService {

    private static final String ENTIDAD_AUDITORIA = "NOTA_INVENTARIO";
    private static final int MAX_BULTOS = 9999;

    private final NotaInventarioRepository notaRepository;
    private final ArchivosAlmacen archivos;
    private final DespachoService despachoService;
    private final Auditoria auditoria;

    public PreparacionNotaService(NotaInventarioRepository notaRepository, ArchivosAlmacen archivos,
                                  DespachoService despachoService, Auditoria auditoria) {
        this.notaRepository = notaRepository;
        this.archivos = archivos;
        this.despachoService = despachoService;
        this.auditoria = auditoria;
    }

    @Transactional
    public NotaInventarioDetalleDto confirmarPicking(UUID noteId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        NotaInventario nota = porPreparar(noteId);
        if (nota.getPickedAt() != null) {
            throw ApiException.badRequest("El picking de esta nota ya esta confirmado.");
        }
        nota.setPickedAt(Instant.now());
        nota.setPickedBy(principal.userId());
        notaRepository.save(nota);
        auditoria.registrar(ENTIDAD_AUDITORIA, noteId, "PICKING", null, Map.of("picking", true), principal.userId());
        return despachoService.obtenerNota(noteId, principal);
    }

    /** Bultos + evidencia (foto o PDF). Se puede corregir mientras la NI no se envie. */
    @Transactional
    public NotaInventarioDetalleDto embalar(UUID noteId, Integer bultos, MultipartFile evidencia,
                                            UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (bultos == null || bultos <= 0 || bultos > MAX_BULTOS) {
            throw ApiException.badRequest("Indica la cantidad de bultos (entre 1 y " + MAX_BULTOS + ").");
        }
        NotaInventario nota = porPreparar(noteId);
        if (nota.getPickedAt() == null) {
            throw ApiException.badRequest("Confirma el picking antes del embalaje.");
        }
        Documento doc = archivos.subir("notas/" + noteId, "EVIDENCIA_EMBALAJE", evidencia, principal.userId());

        Map<String, Object> antes = new HashMap<>();
        antes.put("bultos", nota.getPackageCount());
        antes.put("evidencia", nota.getPackingDocumentId());
        nota.setPackageCount(bultos);
        nota.setPackingDocumentId(doc.getDocumentId());
        nota.setPackedAt(Instant.now());
        nota.setPackedBy(principal.userId());
        notaRepository.save(nota);
        auditoria.registrar(ENTIDAD_AUDITORIA, noteId, "EMBALAJE", antes,
                Map.of("bultos", bultos, "evidencia", doc.getDocumentId()), principal.userId());
        return despachoService.obtenerNota(noteId, principal);
    }

    /** Lock de la fila: picking y embalaje concurrentes no se pisan. Solo NI sin envio. */
    private NotaInventario porPreparar(UUID noteId) {
        NotaInventario nota = notaRepository.bloquear(noteId)
                .orElseThrow(() -> ApiException.notFound("Nota de inventario no encontrada."));
        if (!"EMITIDA".equals(nota.getStatus())) {
            throw ApiException.badRequest("Esta nota ya se envio o no esta vigente.");
        }
        return nota;
    }
}
