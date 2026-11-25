package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.almacen.dto.EntregaDirectaDto;
import com.tdem.logistica.modules.almacen.dto.IncidenciaDto;
import com.tdem.logistica.modules.almacen.dto.RecepcionDto;
import com.tdem.logistica.modules.almacen.dto.RegistrarEntregaDirectaRequest;
import com.tdem.logistica.modules.compras.FlujoOrden;
import com.tdem.logistica.modules.notificaciones.AvisosPlataforma;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
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
import java.util.stream.Collectors;

/**
 * ALM-04 de la V38.7: entrega directa del proveedor al CECO (DEC-L084). La OC
 * marcada {@code direct_delivery} no pasa por el almacen central ni por el
 * kardex: cada CECO registra lo que recibio (guia obligatoria y constancia).
 * Solo cuenta lo aceptado; lo que llega mal es una incidencia del proveedor y
 * queda pendiente para que lo reponga. Registran LOGISTICA y ADMIN, GERENCIA en
 * los requerimientos que puede ver y el dueno del requerimiento lo suyo.
 */
@Service
@Transactional(readOnly = true)
public class EntregaDirectaService {

    private final OrdenAbastecimientoRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final RecepcionRepository recepcionRepository;
    private final RecepcionItemRepository recepcionItemRepository;
    private final GuiaRemisionRepository guiaRepository;
    private final IncidenciaRecepcionRepository incidenciaRepository;
    private final RequerimientoRepository requerimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoRelacionRepository documentoRelacionRepository;
    private final RequerimientoService requerimientoService;
    private final RecepcionService recepcionService;
    private final DestinosEntrega destinosEntrega;
    private final AtencionCalculator atencionCalculator;
    private final ArchivosAlmacen archivos;
    private final Auditoria auditoria;
    private final AvisosPlataforma avisos;
    private final RealtimeNotifier realtimeNotifier;
    private final VentaInterna venta;

    public EntregaDirectaService(VentaInterna venta, OrdenAbastecimientoRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                                 RecepcionRepository recepcionRepository, RecepcionItemRepository recepcionItemRepository,
                                 GuiaRemisionRepository guiaRepository, IncidenciaRecepcionRepository incidenciaRepository,
                                 RequerimientoRepository requerimientoRepository, UsuarioRepository usuarioRepository,
                                 DocumentoRelacionRepository documentoRelacionRepository,
                                 RequerimientoService requerimientoService, RecepcionService recepcionService,
                                 DestinosEntrega destinosEntrega, AtencionCalculator atencionCalculator,
                                 ArchivosAlmacen archivos, Auditoria auditoria, AvisosPlataforma avisos,
                                 RealtimeNotifier realtimeNotifier) {
        this.venta = venta;
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.recepcionRepository = recepcionRepository;
        this.recepcionItemRepository = recepcionItemRepository;
        this.guiaRepository = guiaRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.requerimientoRepository = requerimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.documentoRelacionRepository = documentoRelacionRepository;
        this.requerimientoService = requerimientoService;
        this.recepcionService = recepcionService;
        this.destinosEntrega = destinosEntrega;
        this.atencionCalculator = atencionCalculator;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.realtimeNotifier = realtimeNotifier;
    }

    // ------------------------------------------------------------------ lectura

    /** Toda la entrega de la OC, por CECO (Almacen › recepcion de la OC). */
    public EntregaDirectaDto obtener(UUID orderId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        OrdenAbastecimiento orden = ordenDirecta(orderId);
        return armar(orden, principal, null);
    }

    /** Las entregas directas que traen algo para este requerimiento, solo con sus lineas. */
    public List<EntregaDirectaDto> delRequerimiento(UUID requestId, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(requestId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        requerimientoService.exigirAcceso(r, principal);
        return recepcionItemRepository.ordenesDirectasDelRequerimiento(requestId).stream()
                .map(id -> ordenRepository.findDetalleById(id).orElseThrow())
                .sorted(Comparator.comparing(OrdenAbastecimiento::getCreatedAt))
                .map(o -> armar(o, principal, requestId))
                .toList();
    }

    // ------------------------------------------------------------------ registro

    @Transactional
    public EntregaDirectaDto registrar(UUID orderId, RegistrarEntregaDirectaRequest request, MultipartFile constancia,
                                       UsuarioPrincipal principal) {
        // Lock de la OC: numeracion de la recepcion y control del pendiente sin carreras.
        OrdenAbastecimiento orden = ordenRepository.bloquear(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        if (!orden.isDirectDelivery()) {
            throw ApiException.badRequest("Esta orden se recibe en el almacén, no es de entrega directa.");
        }
        String bloqueo = FlujoOrden.bloqueoRecepcion(orden);
        if (bloqueo != null) {
            throw ApiException.badRequest(bloqueo);
        }
        if (!FlujoOrden.recibible(orden)) {
            throw ApiException.badRequest("Esta orden no admite recepciones (estado " + orden.getStatus() + ").");
        }
        List<OrdenItem> items = ordenItemRepository.findByOrden(orderId);
        List<DestinosEntrega.Destino> destinos = destinosEntrega.destinos(orden, items);
        Map<String, DestinosEntrega.Destino> porClave = destinos.stream()
                .collect(Collectors.toMap(DestinosEntrega.Destino::clave, Function.identity()));
        Map<String, BigDecimal> recibido = recibidoPorDestino(orderId);

        List<Linea> lineas = validarLineas(request, porClave, recibido, principal);
        GuiaValida guia = validarGuia(request);
        boolean conArchivo = constancia != null && !constancia.isEmpty();
        String enlace = limpio(request.enlaceConformidad());
        if (!conArchivo && enlace == null) {
            throw ApiException.badRequest("Adjunta la constancia firmada o registra su enlace.");
        }
        if (enlace != null) {
            RecepcionDestinoService.exigirEnlace(enlace, "El enlace de la constancia firmada no es válido.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Ceco ceco = lineas.get(0).destino().ceco();

        Recepcion recepcion = new Recepcion();
        recepcion.setReceiptId(UUID.randomUUID());
        recepcion.setOrden(orden);
        recepcion.setCecoDestino(ceco);
        recepcion.setReceiptNumber(recepcionRepository.maxNumeroPorOrden(orderId) + 1);
        recepcion.setReceiptDate(Instant.now());
        recepcion.setStatus("PARCIAL");  // se ajusta al final
        recepcion.setRegistrationSource("MANUAL");
        recepcion.setObservation(limpio(request.observation()));
        recepcion.setReceivedBy(usuario);
        recepcion.setConformityUrl(enlace);
        if (conArchivo) {
            Documento doc = archivos.subir("recepciones/" + orderId, "CONFORMIDAD_ENTREGA", constancia, principal.userId());
            DocumentoRelacion rel = new DocumentoRelacion();
            rel.setDocumentRelationId(UUID.randomUUID());
            rel.setDocumento(doc);
            rel.setEntityType("RECEPCION");
            rel.setEntityId(recepcion.getReceiptId());
            documentoRelacionRepository.insertar(rel);
            recepcion.setDocumento(doc);
        }
        recepcionRepository.insertar(recepcion);
        recepcionService.registrarGuia(guia.input(), orden, recepcion);

        boolean observada = false;
        Set<UUID> requerimientos = new LinkedHashSet<>();
        // DEC-L086: lo que el proveedor entrega en un CECO de GOYDEL o METRIN es venta de TDEM a esa empresa.
        var precios = venta.precios(venta.compradora(ceco.getEmpresa()), lineas.stream()
                .map(l -> l.destino().item().getProducto()).filter(java.util.Objects::nonNull)
                .map(p -> p.getProductId()).collect(java.util.stream.Collectors.toSet()));
        for (Linea l : lineas) {
            DestinosEntrega.Destino d = l.destino();
            RecepcionItem ri = null;
            if (l.qty().signum() > 0) {
                ri = new RecepcionItem();
                ri.setReceiptItemId(UUID.randomUUID());
                ri.setRecepcion(recepcion);
                ri.setOrdenItem(d.item());
                ri.setQtyReceived(l.qty());
                ri.setConditionStatus("CONFORME");
                ri.setRequerimientoItem(d.requerimientoItem());
                ri.setNecesidad(d.necesidad());
                if (d.item().getProducto() != null) {
                    ri.setSaleUnitPrice(precios.get(d.item().getProducto().getProductId()));
                }
                recepcionItemRepository.insertar(ri);
                if (d.requerimientoItem() != null) {
                    requerimientos.add(d.requerimientoItem().getRequerimiento().getRequestId());
                }
            }
            if (l.incidencia() != null) {
                recepcionService.crearIncidencia(l.incidencia(), recepcion, d.item(), ri, usuario);
                observada = true;
            }
        }

        String antes = orden.getStatus();
        Map<UUID, BigDecimal> recibidoOc = recepcionItemRepository.recibidoPorItem(
                items.stream().filter(i -> i.getProducto() != null).map(OrdenItem::getOrderItemId).toList());
        boolean completa = items.stream().filter(i -> i.getProducto() != null).allMatch(i ->
                recibidoOc.getOrDefault(i.getOrderItemId(), BigDecimal.ZERO).compareTo(i.getQtyOrdered()) >= 0);
        orden.setStatus(completa ? "RECEPCION_COMPLETA" : "RECEPCION_PARCIAL");
        ordenRepository.save(orden);
        recepcion.setStatus(observada ? "OBSERVADA" : (completa ? "COMPLETA" : "PARCIAL"));
        recepcionRepository.save(recepcion);

        auditoria.registrar("ORDEN_ABASTECIMIENTO", orderId, "ENTREGA_DIRECTA", Map.of("estado", antes),
                Map.of("estado", orden.getStatus(), "recepcion", recepcion.getReceiptNumber(), "ceco", ceco.getCode(),
                        "guia", guia.input().series() + "-" + guia.input().number()),
                principal.userId());
        for (Requerimiento r : requerimientoRepository.findByIdsConDetalle(new ArrayList<>(requerimientos))) {
            avisos.entregaDirecta(r.getRequestId(), orden.getOrderCode(), ceco.getCode(), principal.userId());
            recalcularAtencion(r, principal.userId());
        }
        return armar(orden, principal, null);
    }

    // ------------------------------------------------------------------ armado

    private record Linea(DestinosEntrega.Destino destino, BigDecimal qty,
                         com.tdem.logistica.modules.almacen.dto.IncidenciaInput incidencia) {
    }

    private record GuiaValida(com.tdem.logistica.modules.almacen.dto.GuiaInput input) {
    }

    private List<Linea> validarLineas(RegistrarEntregaDirectaRequest request, Map<String, DestinosEntrega.Destino> porClave,
                                      Map<String, BigDecimal> recibido, UsuarioPrincipal principal) {
        List<Linea> out = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        boolean algo = false;
        for (RegistrarEntregaDirectaRequest.Linea in : request.lineas()) {
            String clave = DestinosEntrega.Destino.clave(in.orderItemId(), in.needId(), in.requestItemId());
            DestinosEntrega.Destino d = porClave.get(clave);
            if (d == null) {
                throw ApiException.badRequest("Una de las líneas no pertenece a esta orden.");
            }
            if (!vistas.add(clave)) {
                throw ApiException.badRequest("Una línea está repetida.");
            }
            if (!d.ceco().getCecoId().equals(request.cecoId())) {
                throw ApiException.badRequest("Registra una recepción por CECO: \"" + d.item().getDescription()
                        + "\" va a otro CECO.");
            }
            Requerimiento r = d.requerimientoItem() != null ? d.requerimientoItem().getRequerimiento() : null;
            if (!puedeRegistrar(r, principal)) {
                throw ApiException.forbidden("No puedes registrar la recepción de \"" + d.item().getDescription() + "\".");
            }
            BigDecimal pendiente = d.asignado().subtract(recibido.getOrDefault(clave, BigDecimal.ZERO));
            if (in.qty().compareTo(pendiente) > 0) {
                throw ApiException.badRequest("La cantidad de \"" + d.item().getDescription() + "\" supera lo pendiente ("
                        + pendiente.stripTrailingZeros().toPlainString() + ").");
            }
            if (in.incidencia() != null) {
                recepcionService.validarIncidencia(in.incidencia(), d.item());
            }
            algo |= in.qty().signum() > 0 || in.incidencia() != null;
            out.add(new Linea(d, in.qty(), in.incidencia()));
        }
        if (!algo) {
            throw ApiException.badRequest("Indica al menos una cantidad recibida o una incidencia.");
        }
        return out;
    }

    private static GuiaValida validarGuia(RegistrarEntregaDirectaRequest request) {
        var g = request.guia();
        if (g == null || limpio(g.series()) == null || limpio(g.number()) == null || g.issueDate() == null) {
            throw ApiException.badRequest("Registra la guía de remisión del proveedor: serie, número y fecha.");
        }
        return new GuiaValida(g);
    }

    /**
     * @param requestId si no es null, solo las lineas de ese requerimiento y las
     *                  recepciones e incidencias que lo tocan.
     */
    private EntregaDirectaDto armar(OrdenAbastecimiento orden, UsuarioPrincipal principal, UUID requestId) {
        UUID orderId = orden.getOrderId();
        List<OrdenItem> items = ordenItemRepository.findByOrden(orderId);
        List<DestinosEntrega.Destino> destinos;
        try {
            destinos = destinosEntrega.destinos(orden, items);
        } catch (ApiException e) {
            destinos = List.of();  // OC que ya no se puede repartir (p. ej. necesidades anuladas): sin lineas
        }
        Map<String, BigDecimal> recibido = recibidoPorDestino(orderId);

        Map<UUID, List<EntregaDirectaDto.Linea>> porCeco = new LinkedHashMap<>();
        Map<UUID, Ceco> cecos = new HashMap<>();
        for (DestinosEntrega.Destino d : destinos) {
            Requerimiento r = d.requerimientoItem() != null ? d.requerimientoItem().getRequerimiento() : null;
            if (requestId != null && (r == null || !requestId.equals(r.getRequestId()))) {
                continue;
            }
            BigDecimal rec = recibido.getOrDefault(d.clave(), BigDecimal.ZERO);
            OrdenItem oi = d.item();
            porCeco.computeIfAbsent(d.ceco().getCecoId(), k -> new ArrayList<>()).add(new EntregaDirectaDto.Linea(
                    oi.getOrderItemId(), d.needId(), d.requestItemId(),
                    r != null ? r.getRequestId() : null,
                    r != null ? r.getRequestCode() : null,
                    r != null && r.getRequester() != null ? r.getRequester().getDisplayName() : null,
                    oi.getVariante() != null ? oi.getVariante().getInternalVariantCode() : oi.getProducto().getSkuBase(),
                    oi.getDescription(),
                    oi.getUom() != null ? oi.getUom().getCode() : null,
                    d.asignado(), rec, d.asignado().subtract(rec).max(BigDecimal.ZERO),
                    puedeRegistrar(r, principal)));
            cecos.putIfAbsent(d.ceco().getCecoId(), d.ceco());
        }
        List<EntregaDirectaDto.Destino> grupos = porCeco.entrySet().stream()
                .map(e -> {
                    Ceco c = cecos.get(e.getKey());
                    return new EntregaDirectaDto.Destino(c.getCecoId(), c.getCode(), c.getName(),
                            c.getEmpresa() != null ? c.getEmpresa().getCode() : null, e.getValue());
                })
                .toList();

        Map<UUID, List<RecepcionItem>> itemsPorRecepcion = recepcionItemRepository.findByOrden(orderId).stream()
                .collect(Collectors.groupingBy(i -> i.getRecepcion().getReceiptId()));
        Map<UUID, GuiaRemision> guias = guiaRepository.findByOrden(orderId).stream()
                .filter(g -> g.getRecepcion() != null)
                .collect(Collectors.toMap(g -> g.getRecepcion().getReceiptId(), Function.identity(), (a, b) -> a));
        List<Recepcion> todas = recepcionRepository.findByOrden(orderId).stream()
                .filter(r -> r.getCecoDestino() != null)
                .filter(r -> requestId == null || itemsPorRecepcion.getOrDefault(r.getReceiptId(), List.of()).stream()
                        .anyMatch(i -> i.getRequerimientoItem() != null
                                && requestId.equals(i.getRequerimientoItem().getRequerimiento().getRequestId())))
                .toList();
        Set<UUID> visibles = todas.stream().map(Recepcion::getReceiptId).collect(Collectors.toSet());
        List<RecepcionDto> recepciones = todas.stream()
                .map(r -> AlmacenMapper.toDto(r, itemsPorRecepcion.getOrDefault(r.getReceiptId(), List.of()),
                        guias.get(r.getReceiptId())))
                .toList();
        List<IncidenciaDto> incidencias = incidenciaRepository.findByOrden(orderId).stream()
                .filter(i -> requestId == null || visibles.contains(i.getRecepcion().getReceiptId()))
                .map(AlmacenMapper::toDto)
                .toList();
        return new EntregaDirectaDto(orderId, orden.getOrderCode(), orden.getProveedor().getLegalName(),
                orden.getProveedor().getRuc(), orden.getStatus(), FlujoOrden.bloqueoRecepcion(orden), grupos,
                recepciones, incidencias);
    }

    // ------------------------------------------------------------------ apoyo

    private Map<String, BigDecimal> recibidoPorDestino(UUID orderId) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (Object[] f : recepcionItemRepository.sumarDirectoPorDestino(orderId)) {
            out.put(DestinosEntrega.Destino.clave((UUID) f[0], (UUID) f[1], (UUID) f[2]), (BigDecimal) f[3]);
        }
        return out;
    }

    /** LOGISTICA/ADMIN siempre; el dueno lo suyo; GERENCIA en lo que puede ver. La OC directa sin REQ, solo Logistica. */
    private boolean puedeRegistrar(Requerimiento r, UsuarioPrincipal p) {
        if (p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            return true;
        }
        if (r == null) {
            return false;
        }
        if (r.getRequester() != null && r.getRequester().getUserId().equals(p.userId())) {
            return true;
        }
        if (!p.tieneAlgunRol(Roles.GERENCIA)) {
            return false;
        }
        try {
            requerimientoService.exigirAcceso(r, p);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private OrdenAbastecimiento ordenDirecta(UUID orderId) {
        OrdenAbastecimiento orden = ordenRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        if (!orden.isDirectDelivery()) {
            throw ApiException.badRequest("Esta orden se recibe en el almacén, no es de entrega directa.");
        }
        return orden;
    }

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

    private static void exigirLogisticaOAdmin(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para ver esta recepción.");
        }
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
