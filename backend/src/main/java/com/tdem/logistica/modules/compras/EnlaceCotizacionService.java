package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Cotizacion;
import com.tdem.logistica.entity.Rfq;
import com.tdem.logistica.entity.RfqEnlace;
import com.tdem.logistica.entity.RfqItem;
import com.tdem.logistica.modules.compras.dto.CotizacionDto;
import com.tdem.logistica.modules.compras.dto.CotizacionItemInput;
import com.tdem.logistica.modules.compras.dto.CotizacionPublicaDto;
import com.tdem.logistica.modules.compras.dto.DocumentoDto;
import com.tdem.logistica.modules.compras.dto.EnlaceCotizacionDto;
import com.tdem.logistica.modules.compras.dto.EnviarCotizacionEnlaceRequest;
import com.tdem.logistica.modules.compras.dto.RegistrarCotizacionRequest;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.CotizacionItemRepository;
import com.tdem.logistica.repository.CotizacionRepository;
import com.tdem.logistica.repository.MonedaRepository;
import com.tdem.logistica.repository.RfqEnlaceRepository;
import com.tdem.logistica.repository.RfqItemRepository;
import com.tdem.logistica.repository.RfqRepository;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enlace unico para que el proveedor cotice sobre las lineas de su RFQ, sin
 * cuenta (DEC-L052). El token (32 bytes aleatorios) solo existe al generarlo:
 * en la BD queda su SHA-256. Las paginas publicas validan el token en cada
 * llamada y no muestran nada interno.
 */
@Service
@Transactional(readOnly = true)
public class EnlaceCotizacionService {

    static final Duration VIGENCIA = Duration.ofDays(30);
    static final String SOURCE_ENLACE = "ENLACE";
    private static final String NO_DISPONIBLE =
            "El enlace no es válido o ya venció. Pide uno nuevo a quien te envió la solicitud.";
    private static final Set<String> CONDICIONES = Set.of("CONTADO", "ANTICIPO", "CREDITO_30");
    private static final Set<String> ENTREGAS = Set.of("AGENCIA", "ALMACEN_PROVEEDOR", "ALMACEN_CLIENTE");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RfqEnlaceRepository enlaceRepository;
    private final RfqRepository rfqRepository;
    private final RfqItemRepository rfqItemRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionItemRepository cotizacionItemRepository;
    private final MonedaRepository monedaRepository;
    private final ComprasService compras;
    private final Auditoria auditoria;
    private final RealtimeNotifier notifier;
    private final String urlPublica;

    public EnlaceCotizacionService(RfqEnlaceRepository enlaceRepository, RfqRepository rfqRepository,
                                   RfqItemRepository rfqItemRepository, CotizacionRepository cotizacionRepository,
                                   CotizacionItemRepository cotizacionItemRepository,
                                   MonedaRepository monedaRepository, ComprasService compras, Auditoria auditoria,
                                   RealtimeNotifier notifier,
                                   @Value("${app.public-url:}") String urlPublica,
                                   @Value("${app.cors.allowed-origins}") String origenes) {
        this.enlaceRepository = enlaceRepository;
        this.rfqRepository = rfqRepository;
        this.rfqItemRepository = rfqItemRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionItemRepository = cotizacionItemRepository;
        this.monedaRepository = monedaRepository;
        this.compras = compras;
        this.auditoria = auditoria;
        this.notifier = notifier;
        // Sin APP_PUBLIC_URL se usa el primer origen permitido (en produccion, el dominio).
        String base = urlPublica != null && !urlPublica.isBlank() ? urlPublica : origenes.split(",")[0];
        this.urlPublica = base.trim().replaceAll("/+$", "");
    }

    // ================================================================ Logistica

    /** Genera el enlace de la RFQ; si habia uno vigente deja de funcionar. */
    @Transactional
    public EnlaceCotizacionDto generar(UUID rfqId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Rfq rfq = rfqRepository.findById(rfqId).orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));
        if ("CERRADA".equals(rfq.getStatus())) {
            throw ApiException.badRequest("La RFQ esta cerrada: ya no recibe cotizaciones.");
        }
        enlaceRepository.findVigenteDeRfq(rfqId).ifPresent(anterior -> {
            anterior.setRevokedAt(Instant.now());
            enlaceRepository.saveAndFlush(anterior);
        });

        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RfqEnlace e = new RfqEnlace();
        e.setLinkId(UUID.randomUUID());
        e.setRfq(rfq);
        e.setTokenHash(hash(token));
        e.setExpiresAt(Instant.now().plus(VIGENCIA));
        e.setCreatedBy(principal.userId());
        enlaceRepository.save(e);
        auditoria.registrar("RFQ", rfqId, "GENERAR_ENLACE", null,
                Map.of("vence", e.getExpiresAt().toString()), principal.userId());
        return new EnlaceCotizacionDto(url(token), token, Instant.now(), e.getExpiresAt(), null, null, true);
    }

    /** Estado del enlace vigente de la RFQ (sin el token, que no se guarda). */
    public EnlaceCotizacionDto vigente(UUID rfqId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        return enlaceRepository.findVigenteDeRfq(rfqId)
                .map(e -> new EnlaceCotizacionDto(null, null, e.getCreatedAt(), e.getExpiresAt(), e.getOpenedAt(),
                        e.getSubmittedAt(), e.getExpiresAt().isAfter(Instant.now())))
                .orElse(null);
    }

    @Transactional
    public void anular(UUID rfqId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        RfqEnlace e = enlaceRepository.findVigenteDeRfq(rfqId)
                .orElseThrow(() -> ApiException.notFound("Esta RFQ no tiene un enlace vigente."));
        e.setRevokedAt(Instant.now());
        auditoria.registrar("RFQ", rfqId, "ANULAR_ENLACE", null, null, principal.userId());
    }

    /** Para el PDF de la RFQ: la URL del token solo si es el enlace vigente de esa RFQ. */
    public String urlSiVigente(UUID rfqId, String token, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        if (token == null || token.isBlank()) return null;
        return enlaceRepository.findByTokenHash(hash(token))
                .filter(e -> e.getRfq().getRfqId().equals(rfqId) && disponible(e))
                .map(e -> url(token))
                .orElseThrow(() -> ApiException.badRequest("El enlace ya no es el vigente: genera uno nuevo."));
    }

    // ================================================================ proveedor (publico)

    @Transactional
    public CotizacionPublicaDto obtener(String token) {
        RfqEnlace e = enlaceDisponible(token);
        if (e.getOpenedAt() == null) {
            e.setOpenedAt(Instant.now());
        }
        return armar(e);
    }

    /**
     * Registra la cotizacion del proveedor con las mismas reglas que Logistica
     * (`ComprasService.registrarCotizacion`). Un reenvio reemplaza al anterior.
     */
    @Transactional
    public CotizacionPublicaDto enviar(String token, EnviarCotizacionEnlaceRequest req, MultipartFile archivo) {
        RfqEnlace e = enlaceDisponible(token);
        Rfq rfq = e.getRfq();
        if (!CONDICIONES.contains(req.paymentCondition())) {
            throw ApiException.badRequest("Condicion de pago no valida.");
        }
        if (!ENTREGAS.contains(req.deliveryMode())) {
            throw ApiException.badRequest("Modalidad de entrega no valida.");
        }
        Map<UUID, RfqItem> lineas = rfqItemRepository.findByRfq(rfq.getRfqId()).stream()
                .collect(Collectors.toMap(RfqItem::getRfqItemId, i -> i));
        List<CotizacionItemInput> items = req.precios().stream().map(p -> {
            RfqItem li = lineas.get(p.rfqItemId());
            if (li == null) {
                throw ApiException.badRequest("Un precio no corresponde a esta solicitud.");
            }
            return new CotizacionItemInput(li.getRfqItemId(), li.getQty(), p.unitCost(),
                    blanco(p.offeredBrand()), blanco(p.offeredModel()));
        }).toList();

        Cotizacion anterior = enviadaPorEnlace(rfq.getRfqId());
        if (anterior != null) {
            if ("SELECCIONADA".equals(anterior.getStatus())) {
                throw ApiException.badRequest("Tu cotizacion ya fue evaluada: para cambiarla comunicate con Logistica.");
            }
            try {
                compras.descartarCotizacionInterna(anterior.getQuotationId());
            } catch (ApiException ex) {
                throw ApiException.badRequest("Tu cotizacion ya fue evaluada: para cambiarla comunicate con Logistica.");
            }
        }
        UUID documentId = null;
        if (archivo != null && !archivo.isEmpty()) {
            DocumentoDto doc = compras.guardarDocumento(archivo, "COTIZACION", e.getCreatedBy());
            documentId = doc.documentId();
        }
        CotizacionDto c = compras.registrarCotizacionInterna(rfq.getRfqId(), new RegistrarCotizacionRequest(
                blanco(req.supplierQuoteNumber()), java.time.LocalDate.now(), req.expirationDate(),
                req.currencyCode(), req.paymentCondition(), req.deliveryMode(), req.leadDays(), documentId, items),
                e.getCreatedBy(), SOURCE_ENLACE);
        e.setSubmittedAt(Instant.now());
        enlaceRepository.save(e);
        auditoria.registrar("COTIZACION", c.quotationId(), "RECIBIR_POR_ENLACE",
                anterior != null ? Map.of("reemplaza", anterior.getQuotationId().toString()) : null,
                Map.of("rfq", rfq.getRfqCode(), "items", items.size()), e.getCreatedBy());
        notifier.cotizacionPorEnlace(e.getCreatedBy(), new RealtimeNotifier.CotizacionRecibida(
                rfq.getRfqId(), rfq.getRfqCode(), rfq.getProveedor().getLegalName()));
        return armar(e);
    }

    // ================================================================ internos

    private CotizacionPublicaDto armar(RfqEnlace e) {
        Rfq rfq = e.getRfq();
        List<RfqItem> items = rfqItemRepository.findByRfq(rfq.getRfqId()).stream()
                .sorted(Comparator.comparing((RfqItem i) -> i.getProducto().getSkuBase())
                        .thenComparing(i -> i.getVariante() != null ? i.getVariante().getInternalVariantCode() : ""))
                .toList();
        List<CotizacionPublicaDto.Linea> lineas = new java.util.ArrayList<>();
        int n = 1;
        for (RfqItem i : items) {
            lineas.add(new CotizacionPublicaDto.Linea(i.getRfqItemId(), n++,
                    i.getVariante() != null ? i.getVariante().getInternalVariantCode() : i.getProducto().getSkuBase(),
                    i.getProducto().getName(),
                    i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                    i.getUom().getCode(), i.getQty(), i.getTechnicalSpecification()));
        }
        Cotizacion enviada = enviadaPorEnlace(rfq.getRfqId());
        CotizacionPublicaDto.Enviada env = null;
        if (enviada != null) {
            Map<UUID, CotizacionPublicaDto.Precio> precios = new LinkedHashMap<>();
            cotizacionItemRepository.findByCotizacion(enviada.getQuotationId()).forEach(ci -> precios.put(
                    ci.getRfqItem().getRfqItemId(), new CotizacionPublicaDto.Precio(ci.getRfqItem().getRfqItemId(),
                            ci.getUnitCost(), ci.getOfferedBrand(), ci.getOfferedModel())));
            env = new CotizacionPublicaDto.Enviada(e.getSubmittedAt(), enviada.getMoneda().getCurrencyCode(),
                    enviada.getPaymentCondition(), enviada.getDeliveryMode(), enviada.getLeadDays(),
                    enviada.getExpirationDate(), enviada.getSupplierQuoteNumber(),
                    enviada.getDocumento() != null ? enviada.getDocumento().getFileName() : null,
                    List.copyOf(precios.values()));
        }
        return new CotizacionPublicaDto(
                rfq.getEmpresa().getLegalName(), rfq.getRfqCode(), rfq.getProveedor().getLegalName(),
                rfq.getProveedor().getRuc(), rfq.getIssueDate(),
                rfq.getCurrencyRequested() != null ? rfq.getCurrencyRequested().getCurrencyCode() : null,
                rfq.getRequestedLeadDays(), rfq.getPaymentCondition(), rfq.getDeliveryMode(), rfq.getDeliveryLocation(),
                e.getExpiresAt(), lineas,
                monedaRepository.findAllByActiveTrueOrderByCurrencyCode().stream().map(ComprasMapper::toDto).toList(),
                env);
    }

    /** Ultima cotizacion vigente que llego por el enlace (las RFQ son de un solo proveedor). */
    private Cotizacion enviadaPorEnlace(UUID rfqId) {
        return cotizacionRepository.findByRfq(rfqId).stream()
                .filter(c -> SOURCE_ENLACE.equals(c.getSource()) && !"DESCARTADA".equals(c.getStatus()))
                .max(Comparator.comparing(Cotizacion::getReceivedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private RfqEnlace enlaceDisponible(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            throw ApiException.notFound(NO_DISPONIBLE);
        }
        return enlaceRepository.findByTokenHash(hash(token)).filter(this::disponible)
                .orElseThrow(() -> ApiException.notFound(NO_DISPONIBLE));
    }

    private boolean disponible(RfqEnlace e) {
        return e.getRevokedAt() == null && e.getExpiresAt().isAfter(Instant.now())
                && !"CERRADA".equals(e.getRfq().getStatus());
    }

    private String url(String token) {
        return urlPublica + "/cotizar/" + token;
    }

    static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blanco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void exigirLogistica(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar solicitudes de cotizacion.");
        }
    }
}
