package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.OcrExtractor;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RFQ, cotizaciones y ordenes de compra. Desde el Lote 16 (DEC-L039) las RFQ
 * nacen de consolidados entre requerimientos (AbastecimientoService); aca se
 * consultan, se registran sus cotizaciones y se gestionan las OC.
 */
@Service
@Transactional(readOnly = true)
public class ComprasService {

    // Sin image/svg+xml, mismo criterio que RequerimientoService.subirImagenProducto.
    private static final Map<String, String> EXTENSIONES_COTIZACION_PERMITIDAS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final NecesidadCompraRepository necesidadRepository;
    private final ConsolidacionRepository consolidacionRepository;
    private final MonedaRepository monedaRepository;
    private final RfqRepository rfqRepository;
    private final RfqItemRepository rfqItemRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionItemRepository cotizacionItemRepository;
    private final OrdenAbastecimientoRepository ordenAbastecimientoRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final RecepcionRepository recepcionRepository;
    private final DocumentoRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final StorageClient storageClient;
    private final OcrExtractor ocrExtractor;
    private final Auditoria auditoria;
    private final OrigenCompras origen;
    private final CajaChicaService cajaChicaService;

    public ComprasService(
            RequerimientoRepository requerimientoRepository,
            RequerimientoItemRepository requerimientoItemRepository,
            NecesidadCompraRepository necesidadRepository,
            ConsolidacionRepository consolidacionRepository,
            MonedaRepository monedaRepository,
            RfqRepository rfqRepository,
            RfqItemRepository rfqItemRepository,
            CotizacionRepository cotizacionRepository,
            CotizacionItemRepository cotizacionItemRepository,
            OrdenAbastecimientoRepository ordenAbastecimientoRepository,
            OrdenItemRepository ordenItemRepository,
            RecepcionRepository recepcionRepository,
            DocumentoRepository documentoRepository,
            UsuarioRepository usuarioRepository,
            StorageClient storageClient,
            OcrExtractor ocrExtractor,
            Auditoria auditoria,
            OrigenCompras origen,
            CajaChicaService cajaChicaService) {
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.necesidadRepository = necesidadRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.monedaRepository = monedaRepository;
        this.rfqRepository = rfqRepository;
        this.rfqItemRepository = rfqItemRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionItemRepository = cotizacionItemRepository;
        this.ordenAbastecimientoRepository = ordenAbastecimientoRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.recepcionRepository = recepcionRepository;
        this.documentoRepository = documentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.storageClient = storageClient;
        this.ocrExtractor = ocrExtractor;
        this.auditoria = auditoria;
        this.origen = origen;
        this.cajaChicaService = cajaChicaService;
    }

    private void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar compras.");
        }
    }

    /** Compras de un requerimiento (detalle del REQ y registro por Caja Chica). */
    public ComprasDetalleDto obtenerDetalleCompra(UUID requestId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Requerimiento r = requerimientoRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        Map<UUID, BigDecimal> pendientes = cajaChicaService.pendientesCajaChica(requestId);
        List<ItemPendienteCompraDto> items = requerimientoItemRepository.findByRequerimiento(requestId).stream()
                .map(i -> ComprasMapper.toDto(i, pendientes.getOrDefault(i.getRequestItemId(), BigDecimal.ZERO)))
                .toList();
        List<Rfq> rfqs = rfqRepository.findPorRequerimiento(requestId);
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origen.deRfqs(rfqs.stream().map(Rfq::getRfqId).toList());
        List<NecesidadDto> necesidades = necesidadRepository.findByRequerimiento(requestId).stream()
                .filter(n -> !NecesidadCompra.ANULADA.equals(n.getStatus()))
                .map(n -> ComprasMapper.toDto(n, n.getQty()))
                .toList();
        return new ComprasDetalleDto(RequerimientoMapper.toDto(r), items,
                rfqs.stream().map(q -> ComprasMapper.toDto(q, refs.getOrDefault(q.getRfqId(), List.of()))).toList(),
                necesidades);
    }

    /** Por enviar, enviadas o respondidas: lo que Logistica tiene que mover. */
    public List<RfqDto> listarRfqsAbiertas(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        List<Rfq> rfqs = rfqRepository.findAbiertas();
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origen.deRfqs(rfqs.stream().map(Rfq::getRfqId).toList());
        return rfqs.stream().map(q -> ComprasMapper.toDto(q, refs.getOrDefault(q.getRfqId(), List.of()))).toList();
    }

    public RfqDetalleDto obtenerRfq(UUID rfqId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        Rfq rfq = rfqRepository.findDetalleById(rfqId)
                .orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));

        List<RfqItemDto> items = rfqItemRepository.findByRfq(rfqId).stream().map(ComprasMapper::toDto).toList();
        // Items de todas las cotizaciones en una consulta (antes una por cotizacion).
        Map<UUID, List<CotizacionItemDto>> itemsPorCotizacion = cotizacionItemRepository.findByRfq(rfqId).stream()
                .collect(Collectors.groupingBy(i -> i.getCotizacion().getQuotationId(),
                        Collectors.mapping(ComprasMapper::toDto, Collectors.toList())));
        List<CotizacionDto> cotizaciones = cotizacionRepository.findByRfq(rfqId).stream()
                .map(c -> ComprasMapper.toDto(c, itemsPorCotizacion.getOrDefault(c.getQuotationId(), List.of())))
                .toList();
        Proveedor p = rfq.getProveedor();
        return new RfqDetalleDto(ComprasMapper.toDto(rfq, origen.deRfq(rfqId)), items, cotizaciones, p.getRuc(),
                rfq.getEmpresa().getCode(), p.getEmail(), p.getWhatsappPhone(), p.getContactName());
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
        return ComprasMapper.toDto(rfq, origen.deRfq(rfqId));
    }

    @Transactional
    public DocumentoDto subirDocumentoCotizacion(MultipartFile file, UsuarioPrincipal principal) {
        return subirDocumento(file, "COTIZACION", principal);
    }

    /** Cotizacion o comprobante de Caja Chica (DEC-L026): mismo storage, distinto tipo y carpeta. */
    @Transactional
    public DocumentoDto subirDocumento(MultipartFile file, String tipo, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        return guardarDocumento(file, tipo, principal.userId());
    }

    /** Guarda el archivo sin validar rol (lo usa tambien el enlace del proveedor, DEC-L052). */
    @Transactional
    DocumentoDto guardarDocumento(MultipartFile file, String tipo, UUID userId) {
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
        documento.setUploadedBy(userId);
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
        return registrarCotizacionInterna(rfqId, request, principal.userId(), "MANUAL");
    }

    /**
     * Registro sin validar rol: lo usan {@link #registrarCotizacion} y el enlace del
     * proveedor (DEC-L052), que valida su token antes. {@code userId} queda como
     * responsable del envio si la RFQ no estaba marcada como enviada.
     */
    @Transactional
    CotizacionDto registrarCotizacionInterna(UUID rfqId, RegistrarCotizacionRequest request, UUID userId, String source) {
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
            if (input.qty().compareTo(rfqItem.getQty()) > 0) {
                throw ApiException.badRequest("La cantidad cotizada de " + rfqItem.getProducto().getSkuBase()
                        + " supera lo solicitado (" + rfqItem.getQty().stripTrailingZeros().toPlainString() + ").");
            }
            Consolidacion c = rfqItem.getConsolidacion();
            if (c != null && !Consolidacion.EN_COTIZACION.equals(c.getStatus())
                    && !Consolidacion.ADJUDICADA.equals(c.getStatus())) {
                throw ApiException.badRequest("El consolidado " + c.getConsolidationCode() + " ya no esta en cotizacion.");
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
        cotizacion.setSource(source);
        cotizacion.setDocumento(documento);
        cotizacion.setStatus("RECIBIDA");
        cotizacionRepository.save(cotizacion);

        for (CotizacionItem item : itemsNuevos) {
            item.setCotizacion(cotizacion);
            cotizacionItemRepository.save(item);
        }

        if ("ENVIADA".equals(rfq.getStatus()) || "BORRADOR".equals(rfq.getStatus())) {
            if (rfq.getSentAt() == null) {
                // Respondio sin que se confirmara el envio: se da por enviada.
                rfq.setSentAt(Instant.now());
                rfq.setSentBy(userId);
                rfq.setSentChannel("OTRO");
            }
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
        return descartarCotizacionInterna(cotizacionId);
    }

    /** Descarte sin validar rol (el enlace del proveedor reemplaza su envio anterior, DEC-L052). */
    @Transactional
    CotizacionDto descartarCotizacionInterna(UUID cotizacionId) {
        Cotizacion cotizacion = cotizacionRepository.findDetalleById(cotizacionId)
                .orElseThrow(() -> ApiException.notFound("Cotizacion no encontrada."));
        if ("SELECCIONADA".equals(cotizacion.getStatus())) {
            throw ApiException.badRequest("No puedes descartar una cotizacion ya seleccionada.");
        }
        if (!consolidacionRepository.bloquearAdjudicadasDe(cotizacionId).isEmpty()) {
            throw ApiException.badRequest("Tiene productos adjudicados: adjudicalos a otra oferta antes de descartarla.");
        }
        cotizacion.setStatus("DESCARTADA");
        cotizacionRepository.save(cotizacion);

        List<CotizacionItemDto> itemDtos = cotizacionItemRepository.findByCotizacion(cotizacionId).stream()
                .map(ComprasMapper::toDto)
                .toList();
        return ComprasMapper.toDto(cotizacion, itemDtos);
    }

    public List<OrdenDto> listarOrdenes(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        List<OrdenAbastecimiento> ordenes = ordenAbastecimientoRepository.findAllConDetalle();
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origen.deOrdenes(
                ordenes.stream().map(OrdenAbastecimiento::getOrderId).toList());
        return ordenes.stream().map(o -> ComprasMapper.toDto(o, refs.getOrDefault(o.getOrderId(), List.of()))).toList();
    }

    public OrdenDetalleDto obtenerOrden(UUID orderId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        OrdenAbastecimiento orden = ordenAbastecimientoRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        List<OrdenItemDto> items = ordenItemRepository.findByOrden(orderId).stream()
                .map(ComprasMapper::toDto)
                .toList();
        return new OrdenDetalleDto(ComprasMapper.toDto(orden, origen.deOrden(orderId)), items,
                orden.getProveedor().getRuc(), orden.getEmpresa().getCode());
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
        // Sus consolidados vuelven a cotizacion sin adjudicar: se elige otra oferta o se anulan (DEC-L039).
        Set<UUID> consolidados = new HashSet<>();
        for (OrdenItem oi : ordenItemRepository.findByOrden(orderId)) {
            if (oi.getConsolidacion() != null) {
                consolidados.add(oi.getConsolidacion().getConsolidationId());
            }
        }
        for (Consolidacion c : consolidados.isEmpty() ? List.<Consolidacion>of()
                : consolidacionRepository.bloquearVarios(consolidados)) {
            if (Consolidacion.ORDENADA.equals(c.getStatus())) {
                c.setStatus(Consolidacion.EN_COTIZACION);
                c.setAdjudicado(null);
                c.setAwardedBy(null);
                c.setAwardedAt(null);
            }
        }
        Cotizacion cot = orden.getCotizacion();
        if ("SELECCIONADA".equals(cot.getStatus())) {
            cot.setStatus("RECIBIDA");
        }
        auditoria.registrar("ORDEN_ABASTECIMIENTO", orderId, "ANULAR", Map.of("estado", anterior),
                Map.of("estado", "ANULADA", "motivo", motivoLimpio), principal.userId());
        return ComprasMapper.toDto(orden, origen.deOrden(orderId));
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
}
