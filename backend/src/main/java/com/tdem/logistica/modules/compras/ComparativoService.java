package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.OcrExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comparativo de cotizaciones (DEC-L047): sobre un grupo de consolidados de una
 * misma empresa, Logistica carga hasta tres cotizaciones (cada una leida por
 * OCR y revisada), las ve lado a lado con el mejor precio marcado y adjudica
 * todo de una vez. Por debajo usa el mismo modelo de siempre: una RFQ por
 * empresa + proveedor, la cotizacion cuelga de ella y se adjudica por
 * consolidado; si el proveedor aun no tenia RFQ para esos productos, se crea.
 */
@Service
public class ComparativoService {

    static final int MAX_COTIZACIONES = 3;

    private final ConsolidacionRepository consolidacionRepository;
    private final RfqItemRepository rfqItemRepository;
    private final CotizacionItemRepository cotizacionItemRepository;
    private final CotizacionRepository cotizacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final AbastecimientoService abastecimiento;
    private final ComprasService compras;
    private final OcrExtractor ocrExtractor;
    private final Auditoria auditoria;
    private final JdbcTemplate jdbc;

    public ComparativoService(ConsolidacionRepository consolidacionRepository, RfqItemRepository rfqItemRepository,
                              CotizacionItemRepository cotizacionItemRepository,
                              CotizacionRepository cotizacionRepository, ProveedorRepository proveedorRepository,
                              UsuarioRepository usuarioRepository, AbastecimientoService abastecimiento,
                              ComprasService compras, OcrExtractor ocrExtractor, Auditoria auditoria,
                              JdbcTemplate jdbc) {
        this.consolidacionRepository = consolidacionRepository;
        this.rfqItemRepository = rfqItemRepository;
        this.cotizacionItemRepository = cotizacionItemRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.abastecimiento = abastecimiento;
        this.compras = compras;
        this.ocrExtractor = ocrExtractor;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
    }

    private static void exigirLogistica(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar compras.");
        }
    }

    // ================================================================ lectura

    @Transactional(readOnly = true)
    public ComparativoDto obtener(List<UUID> ids, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        List<Consolidacion> cs = consolidados(ids);

        List<RfqItem> lineas = rfqItemRepository.findConProveedorByConsolidaciones(ids(cs));
        Map<UUID, List<CotizacionItem>> porLinea = new HashMap<>();
        if (!lineas.isEmpty()) {
            for (CotizacionItem ci : cotizacionItemRepository.findByRfqItems(
                    lineas.stream().map(RfqItem::getRfqItemId).toList())) {
                porLinea.computeIfAbsent(ci.getRfqItem().getRfqItemId(), k -> new ArrayList<>()).add(ci);
            }
        }

        // Una columna por proveedor, en el orden en que entro al comparativo.
        Map<UUID, List<RfqItem>> lineasPorProveedor = new LinkedHashMap<>();
        for (RfqItem li : lineas) {
            if (!"CERRADA".equals(li.getRfq().getStatus()) || !porLinea.getOrDefault(li.getRfqItemId(), List.of()).isEmpty()) {
                lineasPorProveedor.computeIfAbsent(li.getRfq().getProveedor().getSupplierId(), k -> new ArrayList<>()).add(li);
            }
        }

        List<ComparativoDto.Columna> columnas = new ArrayList<>();
        for (List<RfqItem> suyas : lineasPorProveedor.values()) {
            // Por consolidado, la ultima cotizacion vigente (no descartada) de este proveedor.
            Map<UUID, CotizacionItem> vigente = new LinkedHashMap<>();
            for (RfqItem li : suyas) {
                for (CotizacionItem ci : porLinea.getOrDefault(li.getRfqItemId(), List.of())) {
                    if ("DESCARTADA".equals(ci.getCotizacion().getStatus())) {
                        continue;
                    }
                    UUID cid = li.getConsolidacion().getConsolidationId();
                    CotizacionItem actual = vigente.get(cid);
                    if (actual == null || recibida(ci.getCotizacion()).isAfter(recibida(actual.getCotizacion()))) {
                        vigente.put(cid, ci);
                    }
                }
            }
            Cotizacion ultima = vigente.values().stream().map(CotizacionItem::getCotizacion)
                    .max(Comparator.comparing(ComparativoService::recibida)).orElse(null);
            Rfq rfq = ultima != null ? ultima.getRfq() : suyas.get(suyas.size() - 1).getRfq();
            Proveedor p = rfq.getProveedor();
            List<ComparativoDto.Precio> precios = vigente.entrySet().stream()
                    .map(e -> {
                        CotizacionItem ci = e.getValue();
                        return new ComparativoDto.Precio(e.getKey(), ci.getCotizacion().getQuotationId(),
                                ci.getQuotationItemId(), ci.getCotizacion().getMoneda().getCurrencyCode(), ci.getQty(),
                                ci.getUnitCost(), ci.getLineTotal(), ci.getOfferedBrand(), ci.getOfferedModel());
                    })
                    .toList();
            columnas.add(new ComparativoDto.Columna(p.getSupplierId(), p.getLegalName(), p.getRuc(), rfq.getRfqId(),
                    rfq.getRfqCode(),
                    ultima != null ? ultima.getQuotationId() : null,
                    ultima != null ? ultima.getSupplierQuoteNumber() : null,
                    ultima != null ? ultima.getSource() : null,
                    ultima != null ? ultima.getMoneda().getCurrencyCode() : null,
                    ultima != null ? ultima.getPaymentCondition() : null,
                    ultima != null ? ultima.getDeliveryMode() : null,
                    ultima != null ? ultima.getLeadDays() : null,
                    ultima != null && ultima.getDocumento() != null ? ultima.getDocumento().getFileUrl() : null,
                    ultima != null && ultima.getDocumento() != null ? ultima.getDocumento().getFileName() : null,
                    precios));
        }
        // Primero quien cotizo; los que no respondieron, al final.
        columnas.sort(Comparator.comparing((ComparativoDto.Columna c) -> c.quotationId() == null));

        List<ComparativoDto.Fila> filas = cs.stream().map(c -> new ComparativoDto.Fila(
                c.getConsolidationId(), c.getConsolidationCode(), c.getStatus(),
                c.getVariante() != null ? c.getVariante().getInternalVariantCode() : c.getProducto().getSkuBase(),
                c.getProducto().getName(),
                c.getVariante() != null ? c.getVariante().getAttributeValue() : null,
                c.getUom().getCode(), c.getTotalQty(),
                c.getAdjudicado() != null ? c.getAdjudicado().getQuotationItemId() : null,
                c.getAdjudicado() != null ? c.getAdjudicado().getCotizacion().getProveedor().getLegalName() : null))
                .toList();
        return new ComparativoDto(cs.get(0).getEmpresa().getCode(), filas, columnas);
    }

    /** received_at lo pone la BD: una cotizacion recien guardada aun no lo trae y es la mas nueva. */
    private static Instant recibida(Cotizacion c) {
        return c.getReceivedAt() != null ? c.getReceivedAt() : Instant.MAX;
    }

    // ================================================================ OCR

    /** Lee el documento y propone proveedor (por RUC) y costo unitario por consolidado; no guarda nada. */
    @Transactional(readOnly = true)
    public OcrComparativoDto leer(List<UUID> ids, MultipartFile file, UUID supplierIdElegido, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType) && !"image/jpeg".equals(contentType)
                && !"image/png".equals(contentType)) {
            throw ApiException.badRequest("Formato no soportado para leer (usa PDF, JPG o PNG).");
        }
        List<Consolidacion> cs = consolidados(ids);
        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String texto = ocrExtractor.extraerTexto(contenido, contentType);

        // El proveedor (el de la columna o el ya elegido; si no, por su RUC) define que equivalencias usar.
        UUID supplierId = supplierIdElegido;
        String ruc = CotizacionOcrParser.ruc(texto);
        if (supplierId == null && ruc != null) {
            supplierId = proveedorRepository.findAllByActiveTrueOrderByLegalName().stream()
                    .filter(p -> ruc.equals(p.getRuc()))
                    .map(Proveedor::getSupplierId)
                    .findFirst().orElse(null);
        }
        Map<UUID, List<EquivalenciaGuardada>> equivalencias = supplierId != null
                ? equivalenciasDe(supplierId, cs) : Map.of();

        Map<UUID, CotizacionOcrParser.ItemBuscado> buscados = new LinkedHashMap<>();
        for (Consolidacion c : cs) {
            String nombre = c.getProducto().getName()
                    + (c.getVariante() != null && c.getVariante().getAttributeValue() != null
                    ? " " + c.getVariante().getAttributeValue() : "");
            UUID variante = c.getVariante() != null ? c.getVariante().getVariantId() : null;
            List<CotizacionOcrParser.Equivalencia> suyas = equivalencias
                    .getOrDefault(c.getProducto().getProductId(), List.of()).stream()
                    .filter(e -> e.variantId() == null || e.variantId().equals(variante))
                    .map(e -> new CotizacionOcrParser.Equivalencia(e.codigo(), e.clave()))
                    .toList();
            buscados.put(c.getConsolidationId(), new CotizacionOcrParser.ItemBuscado(
                    c.getVariante() != null ? c.getVariante().getInternalVariantCode() : c.getProducto().getSkuBase(),
                    nombre, c.getTotalQty(), suyas));
        }
        OcrCotizacionDto ocr = CotizacionOcrParser.parsearItems(texto, buscados);

        Map<UUID, BigDecimal> precios = new LinkedHashMap<>();
        Map<UUID, OcrComparativoDto.Coincidencia> coincidencias = new LinkedHashMap<>();
        ocr.itemsSugeridos().forEach(i -> {
            precios.put(i.rfqItemId(), i.costoSugerido());
            coincidencias.put(i.rfqItemId(), new OcrComparativoDto.Coincidencia(i.indiceLinea(), i.origen()));
        });
        return new OcrComparativoDto(supplierId, ocr.proveedorDetectado(), ocr.rucDetectado(), ocr.fechaDetectada(),
                ocr.monedaDetectada(), ocr.totalDetectado(), ocr.plazoDetectadoDias(), precios, ocr.textoDetectado(),
                coincidencias, ocr.lineas());
    }

    // ================================================================ escritura

    /**
     * Guarda la cotizacion de un proveedor: usa su RFQ abierta de la empresa (o la crea),
     * le agrega las lineas que falten, pasa a "en cotizacion" los consolidados abiertos y
     * registra la cotizacion con las mismas reglas que desde la RFQ.
     */
    @Transactional
    public ComparativoDto registrar(RegistrarComparativoRequest req, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        List<Consolidacion> cs = consolidados(req.consolidationIds());
        Map<UUID, Consolidacion> porId = cs.stream()
                .collect(Collectors.toMap(Consolidacion::getConsolidationId, c -> c));
        Set<UUID> cotizados = new LinkedHashSet<>();
        for (RegistrarComparativoRequest.Precio p : req.items()) {
            if (!porId.containsKey(p.consolidationId())) {
                throw ApiException.badRequest("Un precio no corresponde a los productos del comparativo.");
            }
            if (!cotizados.add(p.consolidationId())) {
                throw ApiException.badRequest("No repitas el mismo producto dos veces.");
            }
        }
        Proveedor proveedor = proveedorRepository.findById(req.supplierId())
                .filter(Proveedor::isActive)
                .orElseThrow(() -> ApiException.badRequest("Selecciona un proveedor activo."));

        // Lo que ganaba la cotizacion reemplazada pasa a la nueva (si vuelve a cotizarlo).
        List<UUID> readjudicar = List.of();
        if (req.reemplazaQuotationId() != null) {
            Cotizacion anterior = cotizacionRepository.findDetalleById(req.reemplazaQuotationId())
                    .orElseThrow(() -> ApiException.notFound("La cotizacion a reemplazar no existe."));
            if (!anterior.getProveedor().getSupplierId().equals(proveedor.getSupplierId())) {
                throw ApiException.badRequest("Solo puedes reemplazar una cotizacion del mismo proveedor.");
            }
            if ("SELECCIONADA".equals(anterior.getStatus())) {
                throw ApiException.badRequest("Esta cotizacion ya tiene orden de compra: no se puede reemplazar.");
            }
            if (!"DESCARTADA".equals(anterior.getStatus())) {
                readjudicar = compras.liberarAdjudicados(anterior.getQuotationId(), principal.userId());
                compras.descartarCotizacion(anterior.getQuotationId(), principal);
            }
        }

        // Bloqueo en orden fijo para no cruzarse con otra carga del mismo comparativo.
        List<UUID> ordenados = cotizados.stream().distinct().sorted().toList();
        List<Consolidacion> aCotizar = consolidacionRepository.bloquearVarios(ordenados);
        if (aCotizar.size() != ordenados.size()) {
            throw ApiException.notFound("Consolidado no encontrado.");
        }
        for (Consolidacion c : aCotizar) {
            if (!Consolidacion.ABIERTA.equals(c.getStatus()) && !Consolidacion.EN_COTIZACION.equals(c.getStatus())
                    && !Consolidacion.ADJUDICADA.equals(c.getStatus())) {
                throw ApiException.badRequest("El consolidado " + c.getConsolidationCode() + " ya no se puede cotizar.");
            }
        }

        Rfq rfq = rfqDelProveedor(aCotizar, proveedor, req.reemplazaQuotationId() != null, principal);

        Map<UUID, RfqItem> lineaPorConsolidado = new HashMap<>();
        for (RfqItem li : rfqItemRepository.findByRfq(rfq.getRfqId())) {
            if (li.getConsolidacion() != null) {
                lineaPorConsolidado.put(li.getConsolidacion().getConsolidationId(), li);
            }
        }
        List<Consolidacion> faltantes = aCotizar.stream()
                .filter(c -> !lineaPorConsolidado.containsKey(c.getConsolidationId())).toList();
        if (!faltantes.isEmpty()) {
            Map<UUID, String> especificaciones = abastecimiento.especificaciones(ids(faltantes));
            for (Consolidacion c : faltantes) {
                RfqItem li = new RfqItem();
                li.setRfqItemId(UUID.randomUUID());
                li.setRfq(rfq);
                li.setConsolidacion(c);
                li.setProducto(c.getProducto());
                li.setVariante(c.getVariante());
                li.setUom(c.getUom());
                li.setQty(c.getTotalQty());
                li.setTechnicalSpecification(especificaciones.get(c.getConsolidationId()));
                rfqItemRepository.save(li);
                lineaPorConsolidado.put(c.getConsolidationId(), li);
            }
        }
        for (Consolidacion c : aCotizar) {
            if (Consolidacion.ABIERTA.equals(c.getStatus())) {
                c.setStatus(Consolidacion.EN_COTIZACION);
                auditoria.registrar("CONSOLIDACION", c.getConsolidationId(), "COTIZAR",
                        Map.of("estado", Consolidacion.ABIERTA), Map.of("estado", Consolidacion.EN_COTIZACION,
                                "proveedor", proveedor.getLegalName()), principal.userId());
            }
        }
        // Quien cotizo un producto queda habilitado para venderlo (la proxima RFQ ya lo incluye).
        for (Consolidacion c : aCotizar) {
            jdbc.update("""
                    insert into proveedor_producto (product_id, supplier_id, created_by) values (?, ?, ?)
                    on conflict do nothing""", c.getProducto().getProductId(), proveedor.getSupplierId(), principal.userId());
        }
        rfqItemRepository.flush();

        List<CotizacionItemInput> items = req.items().stream().map(p -> new CotizacionItemInput(
                lineaPorConsolidado.get(p.consolidationId()).getRfqItemId(),
                p.qty() != null ? p.qty() : porId.get(p.consolidationId()).getTotalQty(),
                p.unitCost(), blanco(p.offeredBrand()), blanco(p.offeredModel()))).toList();
        CotizacionDto cotizacion = compras.registrarCotizacion(rfq.getRfqId(), new RegistrarCotizacionRequest(
                blanco(req.supplierQuoteNumber()), req.issueDate(), req.expirationDate(), req.currencyCode(),
                req.paymentCondition(), req.deliveryMode(), req.leadDays(), req.documentId(), items), principal);
        aprenderEquivalencias(req, cotizacion, lineaPorConsolidado, porId, proveedor, principal);
        for (UUID cid : readjudicar) {
            RfqItem li = lineaPorConsolidado.get(cid);
            if (li == null) continue;
            cotizacion.items().stream()
                    .filter(i -> li.getRfqItemId().equals(i.rfqItemId()))
                    .findFirst()
                    .ifPresent(i -> abastecimiento.adjudicarLinea(cid, i.quotationItemId(), principal));
        }
        if (req.leidaPorOcr()) {
            cotizacionRepository.findById(cotizacion.quotationId()).ifPresent(c -> c.setSource("OCR"));
        }
        cotizacionRepository.flush();
        return obtener(req.consolidationIds(), principal);
    }

    /** Adjudica cada consolidado al ganador elegido (por defecto, el mejor precio que propone la pantalla). */
    @Transactional
    public ComparativoDto adjudicar(AdjudicarComparativoRequest req, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Set<UUID> delComparativo = new HashSet<>(req.consolidationIds());
        Set<UUID> vistos = new HashSet<>();
        List<AdjudicarComparativoRequest.Eleccion> elecciones = req.elecciones().stream()
                .sorted(Comparator.comparing(AdjudicarComparativoRequest.Eleccion::consolidationId)).toList();
        for (AdjudicarComparativoRequest.Eleccion e : elecciones) {
            if (!delComparativo.contains(e.consolidationId()) || !vistos.add(e.consolidationId())) {
                throw ApiException.badRequest("Eleccion invalida para el comparativo.");
            }
        }
        Map<UUID, Consolidacion> porId = consolidados(req.consolidationIds()).stream()
                .collect(Collectors.toMap(Consolidacion::getConsolidationId, c -> c));
        int adjudicados = 0;
        for (AdjudicarComparativoRequest.Eleccion e : elecciones) {
            Consolidacion c = porId.get(e.consolidationId());
            if (c.getAdjudicado() != null && c.getAdjudicado().getQuotationItemId().equals(e.quotationItemId())) {
                continue;
            }
            abastecimiento.adjudicarLinea(e.consolidationId(), e.quotationItemId(), principal);
            adjudicados++;
        }
        if (adjudicados == 0) {
            throw ApiException.badRequest("Esos productos ya estaban adjudicados a esas ofertas.");
        }
        return obtener(req.consolidationIds(), principal);
    }

    // ================================================================ apoyo

    /** Los consolidados del comparativo: existen, son de una sola empresa y siguen en compras. */
    private List<Consolidacion> consolidados(List<UUID> ids) {
        Set<UUID> unicos = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (unicos.isEmpty()) {
            throw ApiException.badRequest("Selecciona al menos un producto para comparar.");
        }
        if (unicos.size() > 40) {
            throw ApiException.badRequest("Compara como maximo 40 productos a la vez.");
        }
        List<Consolidacion> cs = consolidacionRepository.findDetalleByIds(unicos);
        if (cs.size() != unicos.size()) {
            throw ApiException.notFound("Alguno de los consolidados ya no existe.");
        }
        if (cs.stream().map(c -> c.getEmpresa().getCompanyId()).distinct().count() > 1) {
            throw ApiException.badRequest("Compara productos de una sola empresa: cada empresa compra con su propia RFQ.");
        }
        return cs;
    }

    /**
     * La RFQ abierta de este proveedor en la empresa que ya cubre mas de estos consolidados;
     * si no tiene ninguna, una nueva. Por producto: hasta tres proveedores con cotizacion
     * vigente y una sola vigente por proveedor (la nueva reemplaza a la anterior).
     */
    private Rfq rfqDelProveedor(List<Consolidacion> cs, Proveedor proveedor, boolean reemplaza,
                                UsuarioPrincipal principal) {
        List<RfqItem> lineas = rfqItemRepository.findConProveedorByConsolidaciones(ids(cs));
        Map<UUID, Integer> cobertura = new HashMap<>();
        Map<UUID, Rfq> rfqs = new HashMap<>();
        for (RfqItem li : lineas) {
            Rfq q = li.getRfq();
            if (q.getProveedor().getSupplierId().equals(proveedor.getSupplierId()) && !"CERRADA".equals(q.getStatus())) {
                cobertura.merge(q.getRfqId(), 1, Integer::sum);
                rfqs.put(q.getRfqId(), q);
            }
        }
        exigirCupo(cs, proveedor, reemplaza, lineas);
        return cobertura.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> rfqs.get(e.getKey()))
                .orElseGet(() -> abastecimiento.nuevaRfq(cs.get(0).getEmpresa(), proveedor,
                        usuarioRepository.findById(principal.userId())
                                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."))));
    }

    private void exigirCupo(List<Consolidacion> cs, Proveedor proveedor, boolean reemplaza, List<RfqItem> lineas) {
        if (lineas.isEmpty()) {
            return;
        }
        Map<UUID, Set<UUID>> conCotizacion = new HashMap<>();
        for (CotizacionItem ci : cotizacionItemRepository.findByRfqItems(
                lineas.stream().map(RfqItem::getRfqItemId).toList())) {
            if (!"DESCARTADA".equals(ci.getCotizacion().getStatus())) {
                conCotizacion.computeIfAbsent(ci.getRfqItem().getConsolidacion().getConsolidationId(),
                        k -> new HashSet<>()).add(ci.getCotizacion().getProveedor().getSupplierId());
            }
        }
        for (Consolidacion c : cs) {
            Set<UUID> otros = new HashSet<>(conCotizacion.getOrDefault(c.getConsolidationId(), Set.of()));
            if (otros.remove(proveedor.getSupplierId()) && !reemplaza) {
                throw ApiException.badRequest(proveedor.getLegalName() + " ya cotizo " + c.getConsolidationCode()
                        + ": usa \"Reemplazar\" en su columna para cargar la nueva cotizacion.");
            }
            if (otros.size() >= MAX_COTIZACIONES) {
                throw ApiException.badRequest("El producto " + c.getConsolidationCode() + " ya tiene "
                        + MAX_COTIZACIONES + " cotizaciones: descarta una antes de cargar otra.");
            }
        }
    }

    // ================================================================ equivalencias (DEC-L053)

    /** Equivalencia ya aprendida de un proveedor, lista para buscar en el documento. */
    record EquivalenciaGuardada(UUID productId, UUID variantId, String codigo, String clave) {
    }

    /** Lo aprendido del proveedor para los productos del comparativo, en una consulta. */
    private Map<UUID, List<EquivalenciaGuardada>> equivalenciasDe(UUID supplierId, List<Consolidacion> cs) {
        List<UUID> productos = cs.stream().map(c -> c.getProducto().getProductId()).distinct().toList();
        if (productos.isEmpty()) return Map.of();
        String marcas = String.join(",", Collections.nCopies(productos.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(supplierId);
        args.addAll(productos);
        return jdbc.query("""
                select product_id, variant_id, supplier_code, description_key
                  from proveedor_equivalencia
                 where supplier_id = ? and product_id in (%s)
                 order by last_used_at desc""".formatted(marcas),
                (rs, n) -> new EquivalenciaGuardada(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)), args.toArray())
                .stream().collect(Collectors.groupingBy(EquivalenciaGuardada::productId));
    }

    /**
     * Guarda en cada linea cotizada lo que escribio el proveedor y lo recuerda como
     * equivalencia (la ultima confirmacion de una misma descripcion gana; si cambia
     * de producto, se audita).
     */
    private void aprenderEquivalencias(RegistrarComparativoRequest req, CotizacionDto cotizacion,
                                       Map<UUID, RfqItem> lineaPorConsolidado, Map<UUID, Consolidacion> porId,
                                       Proveedor proveedor, UsuarioPrincipal principal) {
        Map<UUID, UUID> itemPorLinea = new HashMap<>();
        cotizacion.items().forEach(i -> itemPorLinea.put(i.rfqItemId(), i.quotationItemId()));
        List<Object[]> lineas = new ArrayList<>();
        Map<String, RegistrarComparativoRequest.Precio> porClave = new LinkedHashMap<>();
        for (RegistrarComparativoRequest.Precio p : req.items()) {
            String descripcion = blanco(p.supplierDescription());
            String codigo = blanco(p.supplierCode());
            if (descripcion == null && codigo == null) continue;
            UUID quotationItemId = itemPorLinea.get(lineaPorConsolidado.get(p.consolidationId()).getRfqItemId());
            if (quotationItemId != null) lineas.add(new Object[] {codigo, descripcion, quotationItemId});
            String clave = CotizacionOcrParser.clave(descripcion);
            if (descripcion != null && clave.length() >= 3) porClave.put(clave, p);
        }
        if (!lineas.isEmpty()) {
            jdbc.batchUpdate("update cotizacion_item set supplier_code = ?, supplier_description = ? where quotation_item_id = ?",
                    lineas);
        }
        if (porClave.isEmpty()) return;

        String marcas = String.join(",", Collections.nCopies(porClave.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(proveedor.getSupplierId());
        args.addAll(porClave.keySet());
        Map<String, UUID[]> anteriores = new HashMap<>();
        jdbc.query("select description_key, product_id, variant_id from proveedor_equivalencia "
                        + "where supplier_id = ? and description_key in (" + marcas + ")",
                rs -> {
                    anteriores.put(rs.getString(1), new UUID[] {rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)});
                }, args.toArray());

        List<Object[]> filas = new ArrayList<>();
        porClave.forEach((clave, p) -> {
            Consolidacion c = porId.get(p.consolidationId());
            UUID producto = c.getProducto().getProductId();
            UUID variante = c.getVariante() != null ? c.getVariante().getVariantId() : null;
            UUID[] antes = anteriores.get(clave);
            if (antes != null && (!antes[0].equals(producto) || !Objects.equals(antes[1], variante))) {
                Map<String, Object> despues = new LinkedHashMap<>();
                despues.put("descripcion", p.supplierDescription());
                despues.put("producto", producto.toString());
                auditoria.registrar("PROVEEDOR", proveedor.getSupplierId(), "CAMBIAR_EQUIVALENCIA",
                        Map.of("producto", antes[0].toString()), despues, principal.userId());
            }
            filas.add(new Object[] {proveedor.getSupplierId(), producto, variante, blanco(p.supplierCode()),
                    p.supplierDescription().trim(), clave, principal.userId()});
        });
        jdbc.batchUpdate("""
                insert into proveedor_equivalencia (supplier_id, product_id, variant_id, supplier_code,
                  supplier_description, description_key, created_by)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (supplier_id, description_key) do update
                   set product_id = excluded.product_id,
                       variant_id = excluded.variant_id,
                       supplier_code = coalesce(excluded.supplier_code, proveedor_equivalencia.supplier_code),
                       supplier_description = excluded.supplier_description,
                       times_used = proveedor_equivalencia.times_used + 1,
                       last_used_at = now()""", filas);
    }

    private static List<UUID> ids(List<Consolidacion> cs) {
        return cs.stream().map(Consolidacion::getConsolidationId).toList();
    }

    private static String blanco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
