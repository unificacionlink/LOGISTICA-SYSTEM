package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.almacen.dto.EvidenciaRequest;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.almacen.dto.RecepcionDestinoRequest;
import com.tdem.logistica.modules.almacen.dto.ResolverObservadoRequest;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.notificaciones.AvisosPlataforma;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recepcion en destino con diferencias (Lote 18b, ALM-03 de la V38.7, DEC-L063),
 * fiel a opSaveDelivery / aiResolve / aiReceiveReturn con destino ENTREGA
 * DIRECTA: lo recibido queda en el CECO, sin stock de destino (sin subalmacenes).
 * <ul>
 *   <li>Recepcion parcial o total por producto, CONFORME o NO_CONFORME, con la NI
 *       firmada (archivo) o el enlace de la conformidad firmada.</li>
 *   <li>Lo observado se subsana (pasa a aceptado) o se devuelve al almacen
 *       central, donde se recibe en cuarentena.</li>
 *   <li>La atencion del requerimiento cuenta solo lo aceptado (v37ReceivedQty).</li>
 *   <li>Operan LOGISTICA, GERENCIA y ADMIN (V38.7 cvAllowed); GERENCIA, dentro de
 *       los requerimientos que puede ver.</li>
 * </ul>
 */
@Service
public class RecepcionDestinoService {

    public static final String CONFORME = "CONFORME";
    public static final String NO_CONFORME = "NO_CONFORME";
    private static final Set<String> ACCIONES = Set.of("SUBSANAR", "DEVOLVER");
    private static final Set<String> RECIBIBLES = Set.of("EN_TRANSITO", "RECEPCION_PARCIAL");
    private static final Pattern ENLACE = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private final EnvioRepository envioRepository;
    private final SalidaItemRepository salidaItemRepository;
    private final SalidaRepository salidaRepository;
    private final NotaInventarioRepository notaRepository;
    private final RequerimientoRepository requerimientoRepository;
    private final RecepcionDestinoRepository recepcionRepository;
    private final RecepcionDestinoItemRepository recepcionItemRepository;
    private final ResolucionDestinoRepository resolucionRepository;
    private final DocumentoRelacionRepository documentoRelacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArchivosAlmacen archivos;
    private final AtencionCalculator atencionCalculator;
    private final RequerimientoService requerimientoService;
    private final DespachoService despachoService;
    private final Auditoria auditoria;
    private final RealtimeNotifier realtimeNotifier;
    private final AvisosPlataforma avisos;

    public RecepcionDestinoService(EnvioRepository envioRepository, SalidaItemRepository salidaItemRepository,
                                   SalidaRepository salidaRepository, NotaInventarioRepository notaRepository,
                                   RequerimientoRepository requerimientoRepository,
                                   RecepcionDestinoRepository recepcionRepository,
                                   RecepcionDestinoItemRepository recepcionItemRepository,
                                   ResolucionDestinoRepository resolucionRepository,
                                   DocumentoRelacionRepository documentoRelacionRepository,
                                   UsuarioRepository usuarioRepository, ArchivosAlmacen archivos,
                                   AtencionCalculator atencionCalculator, RequerimientoService requerimientoService,
                                   DespachoService despachoService, Auditoria auditoria,
                                   RealtimeNotifier realtimeNotifier,
                                   AvisosPlataforma avisos) {
        this.avisos = avisos;
        this.envioRepository = envioRepository;
        this.salidaItemRepository = salidaItemRepository;
        this.salidaRepository = salidaRepository;
        this.notaRepository = notaRepository;
        this.requerimientoRepository = requerimientoRepository;
        this.recepcionRepository = recepcionRepository;
        this.recepcionItemRepository = recepcionItemRepository;
        this.resolucionRepository = resolucionRepository;
        this.documentoRelacionRepository = documentoRelacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.archivos = archivos;
        this.atencionCalculator = atencionCalculator;
        this.requerimientoService = requerimientoService;
        this.despachoService = despachoService;
        this.auditoria = auditoria;
        this.realtimeNotifier = realtimeNotifier;
    }

    /** Quien opera la recepcion en destino y sus diferencias (V38.7 cvAllowed). */
    public static boolean esOperadorDestino(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.LOGISTICA, Roles.GERENCIA, Roles.ADMIN);
    }

    /** GERENCIA solo sobre los requerimientos que puede ver (V38.7 visibleRequest). */
    void exigirOperador(Requerimiento r, UsuarioPrincipal p) {
        if (!esOperadorDestino(p)) {
            throw ApiException.forbidden("No tienes permiso para registrar la recepcion en destino.");
        }
        if (!p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            requerimientoService.exigirAcceso(r, p);
        }
    }

    // ------------------------------------------------------------------
    // Recepcion (opSaveDelivery)
    // ------------------------------------------------------------------

    @Transactional
    public NotaInventarioDetalleDto registrar(UUID shipmentId, RecepcionDestinoRequest request, MultipartFile notaFirmada,
                                             UsuarioPrincipal principal) {
        Envio envio = bloquearConNota(shipmentId);
        NotaInventario nota = envio.getNotaInventario();
        exigirOperador(nota.getRequerimiento(), principal);
        if (!RECIBIBLES.contains(envio.getStatus())) {
            throw ApiException.badRequest("El envio no admite otra recepcion.");
        }
        if (recepcionRepository.existsByClientToken(request.clientToken())) {
            throw ApiException.badRequest("Esta recepcion ya fue registrada.");
        }
        boolean conArchivo = notaFirmada != null && !notaFirmada.isEmpty();
        String enlace = limpio(request.enlaceConformidad());
        if (!conArchivo && enlace == null) {
            throw ApiException.badRequest("Adjunte la nota firmada o registre su enlace.");
        }
        if (enlace != null) {
            exigirEnlace(enlace, "El enlace de la conformidad firmada no es valido.");
        }
        String resultado = request.resultado().trim().toUpperCase();
        if (!CONFORME.equals(resultado) && !NO_CONFORME.equals(resultado)) {
            throw ApiException.badRequest("Resultado invalido: usa CONFORME o NO_CONFORME.");
        }
        String observacion = limpio(request.observacion());
        if (NO_CONFORME.equals(resultado) && observacion == null) {
            throw ApiException.badRequest("Describe la observacion.");
        }

        Map<UUID, SalidaItem> items = itemsDelEnvio(envio);
        Map<UUID, BigDecimal> cantidades = new LinkedHashMap<>();
        for (RecepcionDestinoRequest.Linea l : request.items()) {
            SalidaItem si = items.get(l.issueItemId());
            if (si == null) {
                throw ApiException.badRequest("Uno de los productos no pertenece a este envio.");
            }
            if (cantidades.put(l.issueItemId(), l.qty()) != null) {
                throw ApiException.badRequest("Un producto esta repetido.");
            }
            if (l.qty().compareTo(si.getQty().subtract(si.getDestReceivedQty())) > 0) {
                throw ApiException.badRequest("Registra cantidades positivas sin superar el saldo enviado.");
            }
        }
        if (cantidades.values().stream().noneMatch(q -> q.signum() > 0)) {
            throw ApiException.badRequest("Registra cantidades positivas sin superar el saldo enviado.");
        }
        Usuario usuario = usuario(principal);

        RecepcionDestino rec = new RecepcionDestino();
        rec.setDestinationReceiptId(UUID.randomUUID());
        rec.setEnvio(envio);
        rec.setReceiptNumber(recepcionRepository.maxNumeroPorEnvio(shipmentId) + 1);
        rec.setClientToken(request.clientToken());
        rec.setResult(resultado);
        rec.setObservation(observacion);
        rec.setConformityUrl(enlace);
        rec.setReceivedBy(usuario);
        if (conArchivo) {
            Documento doc = archivos.subir("envios/" + shipmentId, "CONFORMIDAD_ENTREGA", notaFirmada, principal.userId());
            DocumentoRelacion rel = new DocumentoRelacion();
            rel.setDocumentRelationId(UUID.randomUUID());
            rel.setDocumento(doc);
            rel.setEntityType("ENVIO");
            rel.setEntityId(shipmentId);
            documentoRelacionRepository.insertar(rel);
            rec.setDocumento(doc);
        }
        recepcionRepository.insertar(rec);

        for (Map.Entry<UUID, BigDecimal> c : cantidades.entrySet()) {
            if (c.getValue().signum() == 0) {
                continue;
            }
            SalidaItem si = items.get(c.getKey());
            si.setDestReceivedQty(si.getDestReceivedQty().add(c.getValue()));
            if (CONFORME.equals(resultado)) {
                si.setDestAcceptedQty(si.getDestAcceptedQty().add(c.getValue()));
            } else {
                si.setDestObservedQty(si.getDestObservedQty().add(c.getValue()));
            }
            salidaItemRepository.save(si);
            RecepcionDestinoItem ri = new RecepcionDestinoItem();
            ri.setDestinationReceiptItemId(UUID.randomUUID());
            ri.setRecepcion(rec);
            ri.setSalidaItem(si);
            ri.setQty(c.getValue());
            recepcionItemRepository.insertar(ri);
        }

        String antes = envio.getStatus();
        envio.setReceivedAt(Instant.now());
        envio.setReceivedBy(usuario);
        if (observacion != null) {
            envio.setDeliveryObservation(observacion);
        }
        actualizarEstado(envio, nota, items.values(), false);
        avisos.recepcion(nota.getRequerimiento().getRequestId(), nota.getInventoryNoteCode(), envio.getStatus(),
                principal.userId());
        auditoria.registrar("ENVIO", shipmentId, "RECIBIR", Map.of("estado", antes),
                Map.of("estado", envio.getStatus(), "recepcion", rec.getReceiptNumber(), "resultado", resultado),
                principal.userId());
        recalcularAtencion(nota.getRequerimiento(), principal.userId());
        return despachoService.obtenerNota(nota.getInventoryNoteId(), principal);
    }

    // ------------------------------------------------------------------
    // Diferencias (aiResolve / aiReceiveReturn)
    // ------------------------------------------------------------------

    @Transactional
    public NotaInventarioDetalleDto resolver(UUID shipmentId, ResolverObservadoRequest request, UsuarioPrincipal principal) {
        Envio envio = bloquearConNota(shipmentId);
        NotaInventario nota = envio.getNotaInventario();
        exigirOperador(nota.getRequerimiento(), principal);
        String accion = request.accion().trim().toUpperCase();
        if (!ACCIONES.contains(accion)) {
            throw ApiException.badRequest("Accion invalida: usa SUBSANAR o DEVOLVER.");
        }
        String evidencia = request.evidencia().trim();
        exigirEnlace(evidencia, "Resolucion, cantidad o evidencia no valida.");
        Map<UUID, SalidaItem> items = itemsDelEnvio(envio);
        SalidaItem si = items.get(request.issueItemId());
        if (si == null || request.qty().compareTo(si.getDestObservedQty()) > 0) {
            throw ApiException.badRequest("Resolucion, cantidad o evidencia no valida.");
        }

        si.setDestObservedQty(si.getDestObservedQty().subtract(request.qty()));
        if ("SUBSANAR".equals(accion)) {
            si.setDestAcceptedQty(si.getDestAcceptedQty().add(request.qty()));
        } else {
            si.setDestReturnedQty(si.getDestReturnedQty().add(request.qty()));
        }
        salidaItemRepository.save(si);

        ResolucionDestino res = new ResolucionDestino();
        res.setResolutionId(UUID.randomUUID());
        res.setEnvio(envio);
        res.setSalidaItem(si);
        res.setQty(request.qty());
        res.setAction(accion);
        res.setEvidenceUrl(evidencia);
        res.setStatus("DEVOLVER".equals(accion) ? "EN_TRANSITO" : "RESUELTA");
        res.setCreatedBy(usuario(principal));
        resolucionRepository.insertar(res);

        String antes = envio.getStatus();
        actualizarEstado(envio, nota, items.values(), true);
        auditoria.registrar("ENVIO", shipmentId, accion, Map.of("estado", antes),
                Map.of("estado", envio.getStatus(), "resolucion", res.getResolutionId(), "cantidad", request.qty()),
                principal.userId());
        recalcularAtencion(nota.getRequerimiento(), principal.userId());
        return despachoService.obtenerNota(nota.getInventoryNoteId(), principal);
    }

    /** El almacen central recibe lo devuelto y lo deja en cuarentena (no vuelve a disponible). */
    @Transactional
    public void recibirDevolucion(UUID resolutionId, EvidenciaRequest request, UsuarioPrincipal principal) {
        ResolucionDestino res = resolucionRepository.bloquear(resolutionId)
                .orElseThrow(() -> ApiException.notFound("Devolucion no disponible."));
        Envio envio = envioRepository.findDetalleById(res.getEnvio().getShipmentId())
                .orElseThrow(() -> ApiException.notFound("Devolucion no disponible."));
        exigirOperador(envio.getNotaInventario().getRequerimiento(), principal);
        if (!"DEVOLVER".equals(res.getAction()) || !"EN_TRANSITO".equals(res.getStatus())) {
            throw ApiException.badRequest("Devolucion no disponible.");
        }
        String evidencia = request.evidencia().trim();
        exigirEnlace(evidencia, "Registra evidencia valida.");
        res.setStatus("RECIBIDA_OBSERVADA");
        res.setReceivedBy(usuario(principal));
        res.setReceivedAt(Instant.now());
        res.setReturnEvidenceUrl(evidencia);
        resolucionRepository.save(res);
        auditoria.registrar("DEVOLUCION", resolutionId, "RECIBIR", Map.of("estado", "EN_TRANSITO"),
                Map.of("estado", "RECIBIDA_OBSERVADA"), principal.userId());
    }

    // ------------------------------------------------------------------

    /**
     * Estado del envio y de la NI (V38.7): completo = todo lo enviado recibido.
     * Tras una resolucion, RECEPCIONADO solo si todo quedo aceptado.
     */
    private void actualizarEstado(Envio envio, NotaInventario nota, Collection<SalidaItem> items, boolean trasResolver) {
        boolean completo = items.stream().allMatch(i -> i.getDestReceivedQty().compareTo(i.getQty()) >= 0);
        boolean aceptado = items.stream().allMatch(i -> i.getDestAcceptedQty().compareTo(i.getQty()) >= 0);
        boolean observado = items.stream().anyMatch(i -> i.getDestObservedQty().signum() > 0);
        String estado;
        if (trasResolver) {
            estado = aceptado ? "RECEPCIONADO" : completo ? "RECEPCIONADO_OBSERVADO" : "RECEPCION_PARCIAL";
            envio.setConformityStatus(aceptado ? CONFORME : NO_CONFORME);
        } else {
            estado = completo ? (observado ? "RECEPCIONADO_OBSERVADO" : "RECEPCIONADO") : "RECEPCION_PARCIAL";
            envio.setConformityStatus(observado ? NO_CONFORME : CONFORME);
        }
        envio.setStatus(estado);
        envioRepository.save(envio);
        nota.setStatus(completo ? "RECEPCIONADA" : "RECEPCION_PARCIAL");
        notaRepository.save(nota);
        if (completo && !"CERRADA".equals(envio.getSalida().getStatus())) {
            envio.getSalida().setStatus("CERRADA");
            salidaRepository.save(envio.getSalida());
        }
    }

    /** RF-079 / V38.7 updateRequestCompletionV37: la atencion se recalcula sola. */
    private void recalcularAtencion(Requerimiento r, UUID actor) {
        String nuevoEstado = atencionCalculator.calcular(r);
        if (!nuevoEstado.equals(r.getStatus())) {
            r.setStatus(nuevoEstado);
            requerimientoRepository.save(r);
            if (r.getRequester() != null) {
                realtimeNotifier.decisionTomada(r.getRequester().getUserId(), RequerimientoMapper.toDto(r));
            }
            avisos.atencion(r.getRequestId(), nuevoEstado, actor);
        }
    }

    /** Lock del envio (recepciones y resoluciones concurrentes no se pisan) y luego su detalle. */
    private Envio bloquearConNota(UUID shipmentId) {
        envioRepository.bloquear(shipmentId).orElseThrow(() -> ApiException.notFound("Envio no encontrado."));
        return envioRepository.findDetalleById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Envio no encontrado."));
    }

    private Map<UUID, SalidaItem> itemsDelEnvio(Envio envio) {
        return salidaItemRepository.findBySalida(envio.getSalida().getIssueId()).stream()
                .collect(Collectors.toMap(SalidaItem::getIssueItemId, Function.identity()));
    }

    private Usuario usuario(UsuarioPrincipal principal) {
        return usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
    }

    static void exigirEnlace(String url, String mensaje) {
        if (url == null || !ENLACE.matcher(url).matches()) {
            throw ApiException.badRequest(mensaje);
        }
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
