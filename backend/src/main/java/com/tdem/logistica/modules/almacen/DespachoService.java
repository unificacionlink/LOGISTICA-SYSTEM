package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tdem.logistica.modules.almacen.RecepcionService.exigirLogisticaOAdmin;

/**
 * Despacho de un requerimiento desde almacen: Salida + Nota de Inventario
 * (RF-072/073/074), envio al destino (RF-075/076), adjuntos (RF-077/080) y
 * confirmacion de recepcion en destino (RF-078), que recalcula la atencion
 * del requerimiento (RF-079). Solo se despacha lo reservado para el REQ.
 */
@Service
@Transactional(readOnly = true)
public class DespachoService {

    private static final List<String> ESTADOS_DESPACHABLES =
            List.of(EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);
    private static final Set<String> ESTADOS_BIEN = Set.of("NUEVO", "USADO", "REACONDICIONADO", "OTRO");
    private static final Set<String> MODALIDADES =
            Set.of("AGENCIA", "PROVEEDOR_ENTREGA", "RECOJO", "TERRESTRE_NORMAL", "COURIER", "PROPIO");
    private static final Set<String> TIPOS_ADJUNTO = Set.of("GUIA", "TICKET_ENVIO", "CONFORMIDAD_ENTREGA");
    // Sin image/svg+xml, mismo criterio que las cotizaciones y fotos de producto.
    private static final Map<String, String> EXTENSIONES_PERMITIDAS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final ReservaStockRepository reservaRepository;
    private final SalidaRepository salidaRepository;
    private final SalidaItemRepository salidaItemRepository;
    private final NotaInventarioRepository notaRepository;
    private final EnvioRepository envioRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AlmacenRepository almacenRepository;
    private final EmpresaRepository empresaRepository;
    private final MonedaRepository monedaRepository;
    private final DocumentoRepository documentoRepository;
    private final DocumentoRelacionRepository documentoRelacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final StorageClient storageClient;
    private final AtencionCalculator atencionCalculator;
    private final RealtimeNotifier realtimeNotifier;

    public DespachoService(RequerimientoRepository requerimientoRepository,
                           RequerimientoItemRepository requerimientoItemRepository,
                           ReservaStockRepository reservaRepository,
                           SalidaRepository salidaRepository,
                           SalidaItemRepository salidaItemRepository,
                           NotaInventarioRepository notaRepository,
                           EnvioRepository envioRepository,
                           MovimientoInventarioRepository movimientoRepository,
                           AlmacenRepository almacenRepository,
                           EmpresaRepository empresaRepository,
                           MonedaRepository monedaRepository,
                           DocumentoRepository documentoRepository,
                           DocumentoRelacionRepository documentoRelacionRepository,
                           UsuarioRepository usuarioRepository,
                           StorageClient storageClient,
                           AtencionCalculator atencionCalculator,
                           RealtimeNotifier realtimeNotifier) {
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.reservaRepository = reservaRepository;
        this.salidaRepository = salidaRepository;
        this.salidaItemRepository = salidaItemRepository;
        this.notaRepository = notaRepository;
        this.envioRepository = envioRepository;
        this.movimientoRepository = movimientoRepository;
        this.almacenRepository = almacenRepository;
        this.empresaRepository = empresaRepository;
        this.monedaRepository = monedaRepository;
        this.documentoRepository = documentoRepository;
        this.documentoRelacionRepository = documentoRelacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.storageClient = storageClient;
        this.atencionCalculator = atencionCalculator;
        this.realtimeNotifier = realtimeNotifier;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public List<RequerimientoDto> listarPorDespachar(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        List<UUID> ids = reservaRepository.findRequerimientosConReservaActiva();
        if (ids.isEmpty()) {
            return List.of();
        }
        return requerimientoRepository.findByIdsConDetalle(ids).stream()
                .filter(r -> ESTADOS_DESPACHABLES.contains(r.getStatus()))
                .sorted(Comparator.comparing(Requerimiento::getCreatedAt))
                .map(RequerimientoMapper::toDto)
                .toList();
    }

    public DespachoRequerimientoDto obtenerDespacho(UUID requestId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(requestId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        // Tres consultas por REQ (no tres por item): con la BD lejos, cada ida y vuelta cuesta.
        Map<UUID, BigDecimal> despachado = porItem(salidaItemRepository.sumarDespachadoPorItem(requestId));
        Map<UUID, BigDecimal> entregado = porItem(salidaItemRepository.sumarEntregadoPorItem(requestId));
        Map<UUID, BigDecimal> reservado = porItem(reservaRepository.sumarReservadoPorRequerimiento(requestId));
        List<ItemPorDespacharDto> items = requerimientoItemRepository.findByRequerimiento(requestId).stream()
                .map(i -> new ItemPorDespacharDto(
                        i.getRequestItemId(),
                        i.getProducto().getSkuBase(),
                        i.getProducto().getName(),
                        i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                        i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                        i.getUom().getCode(),
                        i.getQtyRequested(),
                        despachado.getOrDefault(i.getRequestItemId(), BigDecimal.ZERO),
                        entregado.getOrDefault(i.getRequestItemId(), BigDecimal.ZERO),
                        reservado.getOrDefault(i.getRequestItemId(), BigDecimal.ZERO)))
                .toList();
        return new DespachoRequerimientoDto(RequerimientoMapper.toDto(r), items, notasDto(notaRepository.findByRequerimiento(requestId)));
    }

    private static Map<UUID, BigDecimal> porItem(List<Object[]> filas) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] f : filas) {
            m.put((UUID) f[0], (BigDecimal) f[1]);
        }
        return m;
    }

    public List<NotaInventarioDto> listarNotas(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return notasDto(notaRepository.findAllConDetalle());
    }

    public NotaInventarioDetalleDto obtenerNota(UUID noteId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        NotaInventario nota = notaRepository.findDetalleById(noteId)
                .orElseThrow(() -> ApiException.notFound("Nota de inventario no encontrada."));
        Envio envio = envioRepository.findByNota(noteId).orElse(null);
        List<NotaItemDto> items = salidaItemRepository.findBySalida(nota.getSalida().getIssueId()).stream()
                .map(AlmacenMapper::toDto)
                .toList();
        List<DocumentoAdjuntoDto> documentos = envio == null ? List.of()
                : documentoRelacionRepository.findDocumentos("ENVIO", envio.getShipmentId()).stream()
                        .map(rel -> AlmacenMapper.toDto(rel.getDocumento()))
                        .toList();
        return new NotaInventarioDetalleDto(AlmacenMapper.toDto(nota, envio), items, documentos);
    }

    private List<NotaInventarioDto> notasDto(List<NotaInventario> notas) {
        if (notas.isEmpty()) {
            return List.of();
        }
        Map<UUID, Envio> envios = envioRepository
                .findByNotas(notas.stream().map(NotaInventario::getInventoryNoteId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getNotaInventario().getInventoryNoteId(), Function.identity()));
        return notas.stream().map(n -> AlmacenMapper.toDto(n, envios.get(n.getInventoryNoteId()))).toList();
    }

    // ------------------------------------------------------------------
    // Despacho: Salida + Nota de Inventario
    // ------------------------------------------------------------------

    @Transactional
    public NotaInventarioDetalleDto crearNotaInventario(UUID requestId, CrearNotaRequest request,
                                                       UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Requerimiento r = requerimientoRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        if (!ESTADOS_DESPACHABLES.contains(r.getStatus())) {
            throw ApiException.badRequest("Este requerimiento no esta en atencion.");
        }
        if (request.itemState() != null && !ESTADOS_BIEN.contains(request.itemState())) {
            throw ApiException.badRequest("Estado del bien invalido.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        // Lock de la empresa: correlativo NI-<n>-<EMPRESA> sin duplicados en carrera.
        Empresa empresa = empresaRepository.bloquear(r.getEmpresa().getCompanyId())
                .orElseThrow(() -> ApiException.badRequest("Empresa del requerimiento no encontrada."));
        // DEC-L045: un solo almacen central (TDEM) para todas las empresas.
        Almacen almacen = almacenRepository.findCentralDelGrupo()
                .orElseThrow(() -> ApiException.badRequest("No hay un almacen central del grupo configurado."));

        Map<UUID, RequerimientoItem> itemsReq = requerimientoItemRepository.findByRequerimiento(requestId).stream()
                .collect(Collectors.toMap(RequerimientoItem::getRequestItemId, Function.identity()));
        List<DespachoItemInput> aDespachar = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (DespachoItemInput in : request.items()) {
            RequerimientoItem item = itemsReq.get(in.requestItemId());
            if (item == null) {
                throw ApiException.badRequest("Uno de los items no pertenece a este requerimiento.");
            }
            if (!vistos.add(in.requestItemId())) {
                throw ApiException.badRequest("Un item del requerimiento esta repetido.");
            }
            if (in.qty().signum() > 0) {
                aDespachar.add(in);
            }
        }
        if (aDespachar.isEmpty()) {
            throw ApiException.badRequest("Indica al menos una cantidad a despachar.");
        }

        Salida salida = new Salida();
        salida.setIssueId(UUID.randomUUID());
        salida.setEmpresa(empresa);
        salida.setAlmacen(almacen);
        salida.setIssueType(tipoSalida(r.getPurpose()));
        salida.setCeco(r.getCeco());
        salida.setRequerimiento(r);
        salida.setRequiresInvoice(false);
        salida.setStatus("EMITIDA");
        salida.setIssuedAt(Instant.now());
        salida.setCreatedBy(usuario);
        salidaRepository.save(salida);

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValor = BigDecimal.ZERO;
        boolean valorConocido = false;
        for (DespachoItemInput in : aDespachar) {
            RequerimientoItem item = itemsReq.get(in.requestItemId());
            BigDecimal costo = consumirReservas(item, in.qty());

            SalidaItem si = new SalidaItem();
            si.setIssueItemId(UUID.randomUUID());
            si.setSalida(salida);
            si.setProducto(item.getProducto());
            si.setVariante(item.getVariante());
            si.setQty(in.qty());
            si.setRequerimientoItem(item);
            si.setPurchaseCostReference(costo);
            salidaItemRepository.save(si);

            MovimientoInventario mov = new MovimientoInventario();
            mov.setInventoryMovementId(UUID.randomUUID());
            mov.setEmpresa(empresa);
            mov.setAlmacen(almacen);
            mov.setProducto(item.getProducto());
            mov.setVariante(item.getVariante());
            mov.setMovementType("SALIDA");
            mov.setQty(in.qty().negate());
            mov.setUnitCost(costo);
            mov.setReferenceType("SALIDA");
            mov.setReferenceId(salida.getIssueId());
            mov.setCreatedBy(usuario);
            movimientoRepository.save(mov);

            totalQty = totalQty.add(in.qty());
            if (costo != null) {
                totalValor = totalValor.add(costo.multiply(in.qty()));
                valorConocido = true;
            }
        }

        int numero = notaRepository.maxNumeroPorEmpresa(empresa.getCompanyId()) + 1;
        NotaInventario nota = new NotaInventario();
        nota.setInventoryNoteId(UUID.randomUUID());
        nota.setNoteNumber(numero);
        nota.setInventoryNoteCode("NI-" + numero + "-" + empresa.getCode());
        nota.setEmpresa(empresa);
        nota.setRequerimiento(r);
        nota.setSalida(salida);
        nota.setAlmacenOrigen(almacen);
        nota.setCecoDestino(r.getCeco());
        nota.setMovementType(tipoMovimientoNota(r.getPurpose()));
        nota.setPickingDate(LocalDate.now());
        nota.setPickingUser(usuario);
        nota.setItemState(request.itemState() != null ? request.itemState() : "NUEVO");
        nota.setTotalItemQty(totalQty);
        nota.setTotalValueSnapshot(valorConocido ? totalValor : null);
        nota.setPackageCount(request.packageCount());
        nota.setComments(blankToNull(request.comments()));
        nota.setStatus("EMITIDA");
        nota.setIssuedAt(Instant.now());
        nota.setCreatedBy(usuario);
        notaRepository.save(nota);

        return obtenerNota(nota.getInventoryNoteId(), principal);
    }

    /**
     * Consume reservas RESERVADA del item en orden FIFO; si una reserva cubre
     * mas de lo necesario se parte (la parte despachada queda DESPACHADA con
     * el mismo origen, el resto sigue RESERVADA). Devuelve el costo unitario
     * de referencia (de la OC si lo despachado vino de una recepcion).
     */
    private BigDecimal consumirReservas(RequerimientoItem item, BigDecimal qty) {
        List<ReservaStock> reservas = reservaRepository.findActivasParaDespacho(item.getRequestItemId());
        BigDecimal disponible = reservas.stream().map(ReservaStock::getQtyReserved).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (qty.compareTo(disponible) > 0) {
            throw ApiException.badRequest("La cantidad a despachar de " + item.getProducto().getSkuBase()
                    + " supera lo reservado para este requerimiento ("
                    + disponible.stripTrailingZeros().toPlainString() + ").");
        }
        BigDecimal restante = qty;
        BigDecimal costo = null;
        for (ReservaStock reserva : reservas) {
            if (restante.signum() == 0) {
                break;
            }
            if (costo == null && reserva.getRecepcionItem() != null) {
                costo = reserva.getRecepcionItem().getOrdenItem().getUnitCost();
            }
            if (costo == null && reserva.getPettyCashItemId() != null) {
                costo = reservaRepository.costoCajaChica(reserva.getPettyCashItemId());
            }
            BigDecimal tomar = reserva.getQtyReserved().min(restante);
            if (tomar.compareTo(reserva.getQtyReserved()) == 0) {
                reserva.setStatus("DESPACHADA");
                reservaRepository.save(reserva);
            } else {
                reserva.setQtyReserved(reserva.getQtyReserved().subtract(tomar));
                reservaRepository.save(reserva);
                ReservaStock despachada = new ReservaStock();
                despachada.setReservationId(UUID.randomUUID());
                despachada.setRequerimientoItem(reserva.getRequerimientoItem());
                despachada.setAlmacen(reserva.getAlmacen());
                despachada.setProducto(reserva.getProducto());
                despachada.setVariante(reserva.getVariante());
                despachada.setQtyReserved(tomar);
                despachada.setStatus("DESPACHADA");
                despachada.setOrigin(reserva.getOrigin());
                despachada.setRecepcionItem(reserva.getRecepcionItem());
                despachada.setPettyCashItemId(reserva.getPettyCashItemId());
                reservaRepository.save(despachada);
            }
            restante = restante.subtract(tomar);
        }
        return costo;
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    @Transactional
    public NotaInventarioDetalleDto registrarEnvio(UUID noteId, RegistrarEnvioRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        NotaInventario nota = notaRepository.findDetalleById(noteId)
                .orElseThrow(() -> ApiException.notFound("Nota de inventario no encontrada."));
        if (!"EMITIDA".equals(nota.getStatus())) {
            throw ApiException.badRequest("Esta nota ya tiene un envio registrado o no esta vigente.");
        }
        if (envioRepository.findByNota(noteId).isPresent()) {
            throw ApiException.badRequest("Esta nota ya tiene un envio registrado.");
        }
        if (!MODALIDADES.contains(request.deliveryMode())) {
            throw ApiException.badRequest("Modalidad de entrega invalida.");
        }
        if (request.shipDate() != null && request.expectedDate() != null
                && request.expectedDate().isBefore(request.shipDate())) {
            throw ApiException.badRequest("La fecha estimada no puede ser anterior a la fecha de envio.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));

        Envio envio = new Envio();
        envio.setShipmentId(UUID.randomUUID());
        envio.setShipmentType("DESPACHO_REQUERIMIENTO");
        envio.setRequerimiento(nota.getRequerimiento());
        envio.setSalida(nota.getSalida());
        envio.setNotaInventario(nota);
        envio.setDeliveryMode(request.deliveryMode());
        envio.setCarrierName(blankToNull(request.carrierName()));
        envio.setTrackingCode(blankToNull(request.trackingCode()));
        envio.setPackageCount(request.packageCount() != null ? request.packageCount() : nota.getPackageCount());
        envio.setDeclaredWeight(request.declaredWeight());
        aplicarCosto(envio, request);
        envio.setShipDate(request.shipDate() != null ? request.shipDate() : LocalDate.now());
        envio.setExpectedDate(request.expectedDate());
        envio.setStatus("EN_TRANSITO");
        envio.setCreatedBy(usuario);
        envioRepository.save(envio);

        nota.setStatus("DESPACHADA");
        notaRepository.save(nota);
        nota.getSalida().setStatus("DESPACHADA");
        salidaRepository.save(nota.getSalida());

        return obtenerNota(noteId, principal);
    }

    /** RF-076: el Tracking exporta el costo neto; el total (neto + IGV) se guarda para control. */
    private void aplicarCosto(Envio envio, RegistrarEnvioRequest request) {
        if (request.shippingCostNet() == null) {
            if (request.shippingTaxAmount() != null) {
                throw ApiException.badRequest("Indica el costo neto del envio antes del IGV.");
            }
            return;
        }
        String moneda = request.currencyCode() != null && !request.currencyCode().isBlank()
                ? request.currencyCode() : "PEN";
        envio.setMoneda(monedaRepository.findById(moneda)
                .orElseThrow(() -> ApiException.badRequest("Moneda invalida.")));
        BigDecimal igv = request.shippingTaxAmount() != null ? request.shippingTaxAmount() : BigDecimal.ZERO;
        envio.setShippingCostNet(request.shippingCostNet());
        envio.setShippingTaxAmount(igv);
        envio.setShippingCostTotal(request.shippingCostNet().add(igv));
    }

    @Transactional
    public DocumentoAdjuntoDto adjuntarDocumento(UUID shipmentId, String documentType, MultipartFile file,
                                                 UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (!TIPOS_ADJUNTO.contains(documentType)) {
            throw ApiException.badRequest("Tipo de documento invalido (guia, ticket o conformidad).");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        Envio envio = envioRepository.findById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Envio no encontrado."));
        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSIONES_PERMITIDAS.get(contentType) : null;
        if (extension == null) {
            throw ApiException.badRequest("Formato no soportado (usa PDF, JPG, PNG o WEBP).");
        }
        // La extension sale del content-type validado (no del nombre del cliente): sin path traversal.
        String path = "envios/" + envio.getShipmentId() + "/" + UUID.randomUUID() + extension;
        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String url = storageClient.subir(path, contenido, contentType);

        Documento documento = new Documento();
        documento.setDocumentId(UUID.randomUUID());
        documento.setDocumentType(documentType);
        documento.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : path);
        documento.setFileUrl(url);
        documento.setMimeType(contentType);
        documento.setUploadedBy(principal.userId());
        documento.setUploadedAt(Instant.now());
        documento.setStatus("ACTIVO");
        documentoRepository.save(documento);

        DocumentoRelacion rel = new DocumentoRelacion();
        rel.setDocumentRelationId(UUID.randomUUID());
        rel.setDocumento(documento);
        rel.setEntityType("ENVIO");
        rel.setEntityId(envio.getShipmentId());
        documentoRelacionRepository.save(rel);

        return AlmacenMapper.toDto(documento);
    }

    /** RF-078: recepcion en DESTINO (no la de compra). Solo LOGISTICA/ADMIN (DEC-L017). */
    @Transactional
    public NotaInventarioDetalleDto confirmarRecepcion(UUID shipmentId, ConfirmarRecepcionRequest request,
                                                      UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Envio envio = envioRepository.findDetalleById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Envio no encontrado."));
        if (!"EN_TRANSITO".equals(envio.getStatus()) && !"OBSERVADO".equals(envio.getStatus())) {
            throw ApiException.badRequest("Este envio ya fue recepcionado o no esta en transito.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        envio.setStatus("RECEPCIONADO");
        envio.setReceivedAt(Instant.now());
        envio.setReceivedBy(usuario);
        envio.setDeliveryObservation(request != null ? blankToNull(request.observacion()) : null);
        envioRepository.save(envio);

        NotaInventario nota = envio.getNotaInventario();
        nota.setStatus("RECEPCIONADA");
        notaRepository.save(nota);
        envio.getSalida().setStatus("CERRADA");
        salidaRepository.save(envio.getSalida());

        Requerimiento r = nota.getRequerimiento();
        String nuevoEstado = atencionCalculator.calcular(r);
        if (!nuevoEstado.equals(r.getStatus())) {
            r.setStatus(nuevoEstado);
            requerimientoRepository.save(r);
            if (r.getRequester() != null) {
                realtimeNotifier.decisionTomada(r.getRequester().getUserId(), RequerimientoMapper.toDto(r));
            }
        }
        return obtenerNota(nota.getInventoryNoteId(), principal);
    }

    // ------------------------------------------------------------------

    /** purpose del REQ -> SALIDA.issue_type (diccionario V38). */
    private static String tipoSalida(String purpose) {
        return switch (purpose) {
            case "VENTA" -> "VENTA_TERCERO";
            case "PRESTAMO" -> "PRESTAMO";
            default -> "CONSUMO_INTERNO";  // CONSUMO, IMPLEMENTACION
        };
    }

    /** purpose del REQ -> NOTA_INVENTARIO.movement_type. */
    private static String tipoMovimientoNota(String purpose) {
        return switch (purpose) {
            case "VENTA" -> "VENTA";
            case "PRESTAMO" -> "PRESTAMO";
            case "IMPLEMENTACION" -> "TRANSFERENCIA_CECO";
            default -> "CONSUMO";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
