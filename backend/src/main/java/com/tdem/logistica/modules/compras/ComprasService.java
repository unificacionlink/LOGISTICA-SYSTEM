package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.OcrExtractor;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Logica de negocio de Compras (Lote 5): RFQ -> Cotizaciones -> Orden de
 * Compra, para requerimientos que quedaron con faltante tras la verificacion
 * de stock (EN_ATENCION/ATENCION_PARCIAL). Sin consolidacion cruzada entre
 * requerimientos (ver DEC-L013): una RFQ nace de items de UN solo
 * requerimiento.
 */
@Service
@Transactional(readOnly = true)
public class ComprasService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<String> ESTADOS_CON_FALTANTE =
            List.of(EstadoRequerimiento.EN_ATENCION, EstadoRequerimiento.ATENCION_PARCIAL);

    // Sin image/svg+xml, mismo criterio que RequerimientoService.subirImagenProducto.
    private static final Map<String, String> EXTENSIONES_COTIZACION_PERMITIDAS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final ReservaStockRepository reservaStockRepository;
    private final ProveedorRepository proveedorRepository;
    private final MonedaRepository monedaRepository;
    private final RfqRepository rfqRepository;
    private final RfqItemRepository rfqItemRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionItemRepository cotizacionItemRepository;
    private final OrdenAbastecimientoRepository ordenAbastecimientoRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final RecepcionRepository recepcionRepository;
    private final ProductoRepository productoRepository;
    private final DocumentoRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final StorageClient storageClient;
    private final OcrExtractor ocrExtractor;
    private final Auditoria auditoria;

    public ComprasService(
            RequerimientoRepository requerimientoRepository,
            RequerimientoItemRepository requerimientoItemRepository,
            ReservaStockRepository reservaStockRepository,
            ProveedorRepository proveedorRepository,
            MonedaRepository monedaRepository,
            RfqRepository rfqRepository,
            RfqItemRepository rfqItemRepository,
            CotizacionRepository cotizacionRepository,
            CotizacionItemRepository cotizacionItemRepository,
            OrdenAbastecimientoRepository ordenAbastecimientoRepository,
            OrdenItemRepository ordenItemRepository,
            RecepcionRepository recepcionRepository,
            ProductoRepository productoRepository,
            DocumentoRepository documentoRepository,
            UsuarioRepository usuarioRepository,
            StorageClient storageClient,
            OcrExtractor ocrExtractor,
            Auditoria auditoria) {
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.reservaStockRepository = reservaStockRepository;
        this.proveedorRepository = proveedorRepository;
        this.monedaRepository = monedaRepository;
        this.rfqRepository = rfqRepository;
        this.rfqItemRepository = rfqItemRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionItemRepository = cotizacionItemRepository;
        this.ordenAbastecimientoRepository = ordenAbastecimientoRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.recepcionRepository = recepcionRepository;
        this.productoRepository = productoRepository;
        this.documentoRepository = documentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.storageClient = storageClient;
        this.ocrExtractor = ocrExtractor;
        this.auditoria = auditoria;
    }

    private void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar compras.");
        }
    }

    /**
     * Solicitado - cubierto con stock existente (Lote 3, reservado o ya
     * despachado) - en una RFQ activa - ya ordenado. Nunca una columna, igual
     * que StockService.saldoDisponible. Las reservas creadas al recibir una OC
     * no se restan: ya estan contadas en "ya ordenado" (DEC-L017). Tampoco las
     * de Caja Chica: se restan como "comprado por Caja Chica" (DEC-L026).
     */
    public BigDecimal pendienteParaCompra(RequerimientoItem item) {
        BigDecimal reservado = reservaStockRepository.sumarCubiertoPorStockPorItem(item.getRequestItemId());
        BigDecimal enRfqActivo = rfqItemRepository.sumarQtyEnRfqActivo(item.getRequestItemId());
        BigDecimal yaOrdenado = ordenItemRepository.sumarQtyOrdenada(item.getRequestItemId());
        BigDecimal cajaChica = requerimientoItemRepository.sumarCompradoPorCajaChica(item.getRequestItemId());
        return item.getQtyRequested().subtract(reservado).subtract(enRfqActivo).subtract(yaOrdenado).subtract(cajaChica);
    }

    public List<RequerimientoDto> listarPendientes(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        List<UUID> ids = requerimientoItemRepository.findRequerimientosConFaltante(ESTADOS_CON_FALTANTE);
        if (ids.isEmpty()) {
            return List.of();
        }
        return requerimientoRepository.findByIdsConDetalle(ids).stream()
                .sorted(Comparator.comparing(Requerimiento::getCreatedAt))
                .map(RequerimientoMapper::toDto)
                .toList();
    }

    public ComprasDetalleDto obtenerDetalleCompra(UUID requestId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Requerimiento r = requerimientoRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));

        List<ItemPendienteCompraDto> items = requerimientoItemRepository.findByRequerimiento(requestId).stream()
                .map(i -> ComprasMapper.toDto(i, pendienteParaCompra(i)))
                .toList();
        List<RfqDto> rfqs = rfqRepository.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
                .map(ComprasMapper::toDto)
                .toList();
        return new ComprasDetalleDto(RequerimientoMapper.toDto(r), items, rfqs);
    }

    @Transactional
    public RfqDto crearRfq(UUID requestId, CrearRfqRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);

        Requerimiento r = requerimientoRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        Proveedor proveedor = proveedorRepository.findById(request.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Selecciona un proveedor valido."));
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Moneda moneda = null;
        if (request.currencyRequested() != null) {
            moneda = monedaRepository.findById(request.currencyRequested())
                    .orElseThrow(() -> ApiException.badRequest("Selecciona una moneda valida."));
        }

        Map<UUID, RequerimientoItem> itemsPorId = requerimientoItemRepository.findByRequerimiento(requestId).stream()
                .collect(Collectors.toMap(RequerimientoItem::getRequestItemId, i -> i));

        Rfq rfq = nuevaRfq(r, proveedor, usuario);
        rfq.setPaymentCondition(request.paymentCondition());
        rfq.setDeliveryMode(request.deliveryMode());
        rfq.setDeliveryLocation(request.deliveryLocation());
        rfq.setRequestedLeadDays(request.requestedLeadDays());
        rfq.setCurrencyRequested(moneda);
        rfqRepository.save(rfq);

        for (RfqItemInput input : request.items()) {
            RequerimientoItem item = itemsPorId.get(input.requestItemId());
            if (item == null) {
                throw ApiException.badRequest("Uno de los items no pertenece a este requerimiento.");
            }
            BigDecimal pendiente = pendienteParaCompra(item);
            if (input.qty().compareTo(pendiente) > 0) {
                throw ApiException.badRequest(
                        "La cantidad a cotizar de " + item.getProducto().getSkuBase()
                                + " no puede superar lo pendiente de compra (" + pendiente + ").");
            }
            guardarRfqItem(rfq, item, input.qty(), input.technicalSpecification());
        }

        return ComprasMapper.toDto(rfq);
    }

    /**
     * DEC-L018: al aprobarse el requerimiento, el faltante (lo no cubierto con
     * stock) se cotiza solo al proveedor habitual de cada producto; una RFQ
     * por proveedor. Los items sin proveedor habitual quedan en "Pendientes
     * de compra" para RFQ manual. Devuelve las RFQ creadas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Rfq> crearRfqsAutomaticas(Requerimiento r, Usuario usuario) {
        Map<UUID, List<RequerimientoItem>> porProveedor = new LinkedHashMap<>();
        Map<UUID, Proveedor> proveedores = new HashMap<>();
        for (RequerimientoItem item : requerimientoItemRepository.findByRequerimiento(r.getRequestId())) {
            Proveedor habitual = item.getProducto().getProveedorHabitual();
            if (habitual != null && habitual.isActive() && pendienteParaCompra(item).signum() > 0) {
                proveedores.put(habitual.getSupplierId(), habitual);
                porProveedor.computeIfAbsent(habitual.getSupplierId(), k -> new ArrayList<>()).add(item);
            }
        }
        List<Rfq> creadas = new ArrayList<>();
        porProveedor.forEach((supplierId, items) -> {
            Rfq rfq = nuevaRfq(r, proveedores.get(supplierId), usuario);
            rfqRepository.save(rfq);
            for (RequerimientoItem item : items) {
                guardarRfqItem(rfq, item, pendienteParaCompra(item), item.getTechnicalRequirement());
            }
            creadas.add(rfq);
        });
        return creadas;
    }

    private Rfq nuevaRfq(Requerimiento r, Proveedor proveedor, Usuario usuario) {
        Rfq rfq = new Rfq();
        rfq.setRfqId(UUID.randomUUID());
        rfq.setRfqCode(generarCodigo("RFQ", r.getEmpresa().getCode()));
        rfq.setEmpresa(r.getEmpresa());
        rfq.setRequerimiento(r);
        rfq.setProveedor(proveedor);
        rfq.setIssueDate(LocalDate.now());
        rfq.setStatus("ENVIADA");
        rfq.setCreatedBy(usuario);
        return rfq;
    }

    private void guardarRfqItem(Rfq rfq, RequerimientoItem item, BigDecimal qty, String especificacion) {
        RfqItem rfqItem = new RfqItem();
        rfqItem.setRfqItemId(UUID.randomUUID());
        rfqItem.setRfq(rfq);
        rfqItem.setRequerimientoItem(item);
        rfqItem.setProducto(item.getProducto());
        rfqItem.setVariante(item.getVariante());
        rfqItem.setUom(item.getUom());
        rfqItem.setQty(qty);
        rfqItem.setTechnicalSpecification(especificacion);
        rfqItemRepository.save(rfqItem);
    }

    /** RFQ enviadas o respondidas: lo que Logistica tiene que cotizar o decidir. */
    public List<RfqDto> listarRfqsAbiertas(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return rfqRepository.findAbiertas().stream().map(ComprasMapper::toDto).toList();
    }

    public List<ProveedorHabitualDto> listarProveedoresHabituales(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return productoRepository.findConProveedorHabitual().stream()
                .map(p -> new ProveedorHabitualDto(p.getProductId(), p.getProveedorHabitual().getSupplierId(),
                        p.getProveedorHabitual().getLegalName()))
                .toList();
    }

    @Transactional
    public ProveedorHabitualDto asignarProveedorHabitual(UUID productId, UUID supplierId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Producto producto = productoRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        Proveedor proveedor = null;
        if (supplierId != null) {
            proveedor = proveedorRepository.findById(supplierId)
                    .filter(Proveedor::isActive)
                    .orElseThrow(() -> ApiException.badRequest("Selecciona un proveedor activo."));
        }
        producto.setProveedorHabitual(proveedor);
        productoRepository.save(producto);
        return new ProveedorHabitualDto(productId, supplierId, proveedor != null ? proveedor.getLegalName() : null);
    }

    public RfqDetalleDto obtenerRfq(UUID rfqId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Rfq rfq = rfqRepository.findDetalleById(rfqId)
                .orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));

        List<RfqItemDto> items = rfqItemRepository.findByRfq(rfqId).stream().map(ComprasMapper::toDto).toList();
        List<CotizacionDto> cotizaciones = cotizacionRepository.findByRfq(rfqId).stream()
                .map(c -> ComprasMapper.toDto(c, cotizacionItemRepository.findByCotizacion(c.getQuotationId()).stream()
                        .map(ComprasMapper::toDto)
                        .toList()))
                .toList();
        return new RfqDetalleDto(ComprasMapper.toDto(rfq), items, cotizaciones, rfq.getProveedor().getRuc(),
                rfq.getEmpresa().getCode());
    }

    @Transactional
    public RfqDto cerrarRfq(UUID rfqId, String motivo, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        String motivoLimpio = exigirMotivo(motivo, "cerrar la RFQ");
        Rfq rfq = rfqRepository.findDetalleById(rfqId)
                .orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));
        if ("CERRADA".equals(rfq.getStatus())) {
            throw ApiException.badRequest("Esta RFQ ya esta cerrada.");
        }
        String anterior = rfq.getStatus();
        rfq.setStatus("CERRADA");
        rfqRepository.save(rfq);
        auditoria.registrar("RFQ", rfqId, "CERRAR", Map.of("estado", anterior),
                Map.of("estado", "CERRADA", "motivo", motivoLimpio), principal.userId());
        return ComprasMapper.toDto(rfq);
    }

    @Transactional
    public DocumentoDto subirDocumentoCotizacion(MultipartFile file, UsuarioPrincipal principal) {
        return subirDocumento(file, "COTIZACION", principal);
    }

    /** Cotizacion o comprobante de Caja Chica (DEC-L026): mismo storage, distinto tipo y carpeta. */
    @Transactional
    public DocumentoDto subirDocumento(MultipartFile file, String tipo, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (!"COTIZACION".equals(tipo) && !"COMPROBANTE_CAJA_CHICA".equals(tipo)) {
            throw ApiException.badRequest("Tipo de documento invalido.");
        }
        if (file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSIONES_COTIZACION_PERMITIDAS.get(contentType) : null;
        if (extension == null) {
            throw ApiException.badRequest("Formato no soportado (usa PDF, JPG, PNG o WEBP).");
        }

        String nombreOriginal = file.getOriginalFilename();
        String path = ("COTIZACION".equals(tipo) ? "cotizaciones/" : "caja-chica/") + UUID.randomUUID() + extension;
        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String url = storageClient.subir(path, contenido, contentType);

        Documento documento = new Documento();
        documento.setDocumentId(UUID.randomUUID());
        documento.setDocumentType(tipo);
        documento.setFileName(nombreOriginal != null ? nombreOriginal : path);
        documento.setFileUrl(url);
        documento.setMimeType(contentType);
        documento.setUploadedBy(principal.userId());
        documento.setUploadedAt(Instant.now());
        documento.setStatus("ACTIVO");
        documentoRepository.save(documento);

        return new DocumentoDto(documento.getDocumentId(), documento.getFileUrl(), documento.getFileName());
    }

    /**
     * OCR gratuito y local (Tesseract) sobre el mismo archivo que se piensa
     * adjuntar: solo propone valores, nunca los guarda — el usuario siempre
     * revisa/corrige en el formulario antes de "Registrar cotizacion".
     */
    public OcrCotizacionDto extraerOcr(UUID rfqId, MultipartFile file, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType) && !"image/jpeg".equals(contentType)
                && !"image/png".equals(contentType)) {
            throw ApiException.badRequest("Formato no soportado para OCR (usa PDF, JPG o PNG).");
        }
        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        // Codigos ya enviados en la RFQ: buscarlos en el texto es mas confiable entre
        // proveedores distintos que tratar de "entender" una tabla con formato libre.
        Map<UUID, String> codigosPorItem = rfqItemRepository.findByRfq(rfqId).stream()
                .collect(Collectors.toMap(RfqItem::getRfqItemId, i -> i.getVariante() != null
                        ? i.getVariante().getInternalVariantCode() : i.getProducto().getSkuBase()));
        String texto = ocrExtractor.extraerTexto(contenido, contentType);
        return CotizacionOcrParser.parsear(texto, codigosPorItem);
    }

    @Transactional
    public CotizacionDto registrarCotizacion(UUID rfqId, RegistrarCotizacionRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Rfq rfq = rfqRepository.findDetalleById(rfqId)
                .orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));
        if ("CERRADA".equals(rfq.getStatus())) {
            throw ApiException.badRequest("Esta RFQ ya esta cerrada, no se pueden registrar mas cotizaciones.");
        }
        Moneda moneda = monedaRepository.findById(request.currencyCode())
                .orElseThrow(() -> ApiException.badRequest("Selecciona una moneda valida."));
        Documento documento = null;
        if (request.documentId() != null) {
            documento = documentoRepository.findById(request.documentId())
                    .orElseThrow(() -> ApiException.badRequest("El documento adjunto no es valido."));
        }

        Map<UUID, RfqItem> itemsPorId = rfqItemRepository.findByRfq(rfqId).stream()
                .collect(Collectors.toMap(RfqItem::getRfqItemId, i -> i));

        Set<UUID> yaCotizados = new HashSet<>();
        List<CotizacionItem> itemsNuevos = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CotizacionItemInput input : request.items()) {
            RfqItem rfqItem = itemsPorId.get(input.rfqItemId());
            if (rfqItem == null) {
                throw ApiException.badRequest("Uno de los items cotizados no pertenece a esta RFQ.");
            }
            if (!yaCotizados.add(input.rfqItemId())) {
                throw ApiException.badRequest("No repitas el mismo item cotizado dos veces.");
            }
            BigDecimal lineTotal = input.qty().multiply(input.unitCost()).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            CotizacionItem item = new CotizacionItem();
            item.setQuotationItemId(UUID.randomUUID());
            item.setRfqItem(rfqItem);
            item.setProducto(rfqItem.getProducto());
            item.setVariante(rfqItem.getVariante());
            item.setQty(input.qty());
            item.setUnitCost(input.unitCost());
            item.setLineTotal(lineTotal);
            item.setOfferedBrand(input.offeredBrand());
            item.setOfferedModel(input.offeredModel());
            itemsNuevos.add(item);
        }

        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setQuotationId(UUID.randomUUID());
        cotizacion.setRfq(rfq);
        cotizacion.setProveedor(rfq.getProveedor());
        cotizacion.setSupplierQuoteNumber(request.supplierQuoteNumber());
        cotizacion.setIssueDate(request.issueDate());
        cotizacion.setExpirationDate(request.expirationDate());
        cotizacion.setMoneda(moneda);
        cotizacion.setSubtotal(subtotal);
        cotizacion.setTaxAmount(BigDecimal.ZERO);
        cotizacion.setTotal(subtotal);
        cotizacion.setPaymentCondition(request.paymentCondition());
        cotizacion.setDeliveryMode(request.deliveryMode());
        cotizacion.setLeadDays(request.leadDays());
        cotizacion.setSource("MANUAL");
        cotizacion.setDocumento(documento);
        cotizacion.setStatus("RECIBIDA");
        cotizacionRepository.save(cotizacion);

        for (CotizacionItem item : itemsNuevos) {
            item.setCotizacion(cotizacion);
            cotizacionItemRepository.save(item);
        }

        if ("ENVIADA".equals(rfq.getStatus()) || "BORRADOR".equals(rfq.getStatus())) {
            rfq.setStatus("RESPONDIDA");
            rfqRepository.save(rfq);
        }

        List<CotizacionItemDto> itemDtos = cotizacionItemRepository.findByCotizacion(cotizacion.getQuotationId())
                .stream()
                .map(ComprasMapper::toDto)
                .toList();
        return ComprasMapper.toDto(cotizacion, itemDtos);
    }

    @Transactional
    public CotizacionDto descartarCotizacion(UUID cotizacionId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Cotizacion cotizacion = cotizacionRepository.findDetalleById(cotizacionId)
                .orElseThrow(() -> ApiException.notFound("Cotizacion no encontrada."));
        if ("SELECCIONADA".equals(cotizacion.getStatus())) {
            throw ApiException.badRequest("No puedes descartar una cotizacion ya seleccionada.");
        }
        cotizacion.setStatus("DESCARTADA");
        cotizacionRepository.save(cotizacion);

        List<CotizacionItemDto> itemDtos = cotizacionItemRepository.findByCotizacion(cotizacionId).stream()
                .map(ComprasMapper::toDto)
                .toList();
        return ComprasMapper.toDto(cotizacion, itemDtos);
    }

    /** Emite la Orden de Compra a partir de la cotizacion elegida; descarta a sus hermanas y cierra la RFQ. */
    @Transactional
    public OrdenDto seleccionarCotizacion(UUID cotizacionId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Cotizacion cotizacion = cotizacionRepository.findDetalleById(cotizacionId)
                .orElseThrow(() -> ApiException.notFound("Cotizacion no encontrada."));
        if ("SELECCIONADA".equals(cotizacion.getStatus())) {
            throw ApiException.badRequest("Esta cotizacion ya fue seleccionada.");
        }
        if ("DESCARTADA".equals(cotizacion.getStatus())) {
            throw ApiException.badRequest("Esta cotizacion fue descartada.");
        }
        List<CotizacionItem> items = cotizacionItemRepository.findByCotizacion(cotizacionId);
        if (items.isEmpty()) {
            throw ApiException.badRequest("Esta cotizacion no tiene items.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));

        cotizacion.setStatus("SELECCIONADA");
        cotizacionRepository.save(cotizacion);

        Rfq rfq = cotizacion.getRfq();
        cotizacionRepository.findByRfq(rfq.getRfqId()).stream()
                .filter(c -> !c.getQuotationId().equals(cotizacionId))
                .filter(c -> !"DESCARTADA".equals(c.getStatus()) && !"SELECCIONADA".equals(c.getStatus()))
                .forEach(c -> {
                    c.setStatus("DESCARTADA");
                    cotizacionRepository.save(c);
                });
        rfq.setStatus("CERRADA");
        rfqRepository.save(rfq);

        OrdenAbastecimiento orden = new OrdenAbastecimiento();
        orden.setOrderId(UUID.randomUUID());
        orden.setOrderCode(generarCodigo("OC", rfq.getRequerimiento().getEmpresa().getCode()));
        orden.setEmpresa(rfq.getRequerimiento().getEmpresa());
        orden.setProveedor(cotizacion.getProveedor());
        orden.setOrderType("OC_NORMAL");
        orden.setCotizacion(cotizacion);
        orden.setIssueDate(LocalDate.now());
        orden.setPaymentCondition(cotizacion.getPaymentCondition());
        orden.setDeliveryMode(cotizacion.getDeliveryMode());
        orden.setDeliveryLocation(rfq.getDeliveryLocation());
        orden.setMoneda(cotizacion.getMoneda());
        orden.setSubtotal(cotizacion.getSubtotal());
        orden.setTaxAmount(cotizacion.getTaxAmount());
        orden.setTotal(cotizacion.getTotal());
        orden.setStatus("EMITIDA");
        orden.setCreatedBy(usuario);
        ordenAbastecimientoRepository.save(orden);

        for (CotizacionItem ci : items) {
            RequerimientoItem requerimientoItem = ci.getRfqItem().getRequerimientoItem();
            String atributo = ci.getVariante() != null ? " (" + ci.getVariante().getAttributeValue() + ")" : "";
            OrdenItem orderItem = new OrdenItem();
            orderItem.setOrderItemId(UUID.randomUUID());
            orderItem.setOrden(orden);
            orderItem.setRequerimientoItem(requerimientoItem);
            orderItem.setProducto(ci.getProducto());
            orderItem.setVariante(ci.getVariante());
            orderItem.setDescription(ci.getProducto().getName() + atributo);
            orderItem.setUom(ci.getRfqItem().getUom());
            orderItem.setQtyOrdered(ci.getQty());
            orderItem.setUnitCost(ci.getUnitCost());
            orderItem.setLineSubtotal(ci.getLineTotal());
            orderItem.setTaxAmount(BigDecimal.ZERO);
            orderItem.setLineTotal(ci.getLineTotal());
            ordenItemRepository.save(orderItem);

            // DEC-L018: la primera compra de un SKU fija su proveedor habitual
            // (la siguiente vez la RFQ sale sola). Editable en el Catalogo.
            if (ci.getProducto().getProveedorHabitual() == null) {
                ci.getProducto().setProveedorHabitual(cotizacion.getProveedor());
                productoRepository.save(ci.getProducto());
            }
        }

        return ComprasMapper.toDto(orden);
    }

    public List<OrdenDto> listarOrdenes(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return ordenAbastecimientoRepository.findAllConDetalle().stream().map(ComprasMapper::toDto).toList();
    }

    public OrdenDetalleDto obtenerOrden(UUID orderId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        OrdenAbastecimiento orden = ordenAbastecimientoRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        List<OrdenItemDto> items = ordenItemRepository.findByOrden(orderId).stream()
                .map(ComprasMapper::toDto)
                .toList();
        return new OrdenDetalleDto(ComprasMapper.toDto(orden), items, orden.getProveedor().getRuc(),
                orden.getEmpresa().getCode());
    }

    @Transactional
    public OrdenDto anularOrden(UUID orderId, String motivo, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        String motivoLimpio = exigirMotivo(motivo, "anular la orden");
        OrdenAbastecimiento orden = ordenAbastecimientoRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        if ("ANULADA".equals(orden.getStatus())) {
            throw ApiException.badRequest("Esta orden ya esta anulada.");
        }
        if (recepcionRepository.existsByOrden_OrderId(orderId)) {
            throw ApiException.badRequest("No se puede anular: la orden ya tiene recepciones en almacen.");
        }
        String anterior = orden.getStatus();
        orden.setStatus("ANULADA");
        ordenAbastecimientoRepository.save(orden);
        auditoria.registrar("ORDEN_ABASTECIMIENTO", orderId, "ANULAR", Map.of("estado", anterior),
                Map.of("estado", "ANULADA", "motivo", motivoLimpio), principal.userId());
        return ComprasMapper.toDto(orden);
    }

    /** RF-064: las operaciones irreversibles exigen motivo. */
    private static String exigirMotivo(String motivo, String accion) {
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Indica el motivo para " + accion + ".");
        }
        return motivo.trim();
    }

    public List<MonedaDto> listarMonedas(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return monedaRepository.findAllByActiveTrueOrderByCurrencyCode().stream().map(ComprasMapper::toDto).toList();
    }

    /** Codigo legible unico por empresa: <prefijo>-<empresa>-<yyyyMMdd>-<sufijo>. */
    private String generarCodigo(String prefijo, String empresaCode) {
        String fecha = LocalDate.now().format(YYYYMMDD);
        String sufijo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return prefijo + "-" + empresaCode + "-" + fecha + "-" + sufijo;
    }
}
