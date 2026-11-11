package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lote 16 (DEC-L039, V38.7): necesidad de compra -> consolidacion entre
 * requerimientos -> RFQ multiproducto por proveedor -> adjudicacion por
 * producto -> OC por cotizacion.
 * <ul>
 *   <li>Al aprobar, el faltante de cada item (lo no reservado de stock) crea una
 *       necesidad. Si la regla de Caja Chica no aplica entra sola al
 *       consolidado ABIERTO de su empresa + SKU + unidad; si aplica queda
 *       PENDIENTE_RUTA hasta que Logistica decida.</li>
 *   <li>Preparar solicitudes cierra el consolidado (EN_COTIZACION) y agrega una
 *       linea por cada proveedor habilitado del producto a la RFQ BORRADOR de
 *       esa empresa + proveedor (una RFQ, varios productos).</li>
 *   <li>Cada consolidado se adjudica a una linea cotizada (proveedores
 *       distintos por producto) y la OC se emite por cotizacion, con todos sus
 *       productos adjudicados.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AbastecimientoService {

    private static final Set<String> RUTAS = Set.of(NecesidadCompra.COMPRA, NecesidadCompra.CAJA_CHICA);
    private static final Set<String> CANALES = Set.of("CORREO", "WHATSAPP", "OTRO");
    private static final List<String> CONSOLIDADOS_VIGENTES =
            List.of(Consolidacion.ABIERTA, Consolidacion.EN_COTIZACION, Consolidacion.ADJUDICADA);

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final NecesidadCompraRepository necesidadRepository;
    private final ConsolidacionRepository consolidacionRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final ReservaStockRepository reservaStockRepository;
    private final RfqRepository rfqRepository;
    private final RfqItemRepository rfqItemRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CotizacionItemRepository cotizacionItemRepository;
    private final OrdenAbastecimientoRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CajaChicaService cajaChicaService;
    private final OrigenCompras origen;
    private final Auditoria auditoria;

    public AbastecimientoService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named,
                                 NecesidadCompraRepository necesidadRepository,
                                 ConsolidacionRepository consolidacionRepository,
                                 RequerimientoItemRepository requerimientoItemRepository,
                                 ReservaStockRepository reservaStockRepository, RfqRepository rfqRepository,
                                 RfqItemRepository rfqItemRepository, CotizacionRepository cotizacionRepository,
                                 CotizacionItemRepository cotizacionItemRepository,
                                 OrdenAbastecimientoRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                                 ProveedorRepository proveedorRepository, ProductoRepository productoRepository,
                                 UsuarioRepository usuarioRepository, CajaChicaService cajaChicaService,
                                 OrigenCompras origen, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.named = named;
        this.necesidadRepository = necesidadRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.reservaStockRepository = reservaStockRepository;
        this.rfqRepository = rfqRepository;
        this.rfqItemRepository = rfqItemRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.cotizacionItemRepository = cotizacionItemRepository;
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.cajaChicaService = cajaChicaService;
        this.origen = origen;
        this.auditoria = auditoria;
    }

    private static void exigirLogistica(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar compras.");
        }
    }

    // ================================================================ necesidades

    /**
     * Lo llama la aprobacion, despues de reservar stock y en su transaccion.
     * Reemplaza la RFQ automatica al proveedor habitual (DEC-L018).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void generarNecesidades(Requerimiento r) {
        // Lo cubierto por stock de todos los items y la regla de Caja Chica, una vez.
        Map<UUID, BigDecimal> cubierto = new HashMap<>();
        for (Object[] fila : reservaStockRepository.sumarCubiertoPorStockPorRequerimiento(r.getRequestId())) {
            cubierto.put((UUID) fila[0], (BigDecimal) fila[1]);
        }
        var sugerir = cajaChicaService.sugeridor(r);
        for (RequerimientoItem item : requerimientoItemRepository.findByRequerimiento(r.getRequestId())) {
            BigDecimal faltante = item.getQtyRequested()
                    .subtract(cubierto.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO));
            if (faltante.signum() <= 0) {
                continue;
            }
            NecesidadCompra n = new NecesidadCompra();
            n.setNeedId(UUID.randomUUID());
            n.setRequerimientoItem(item);
            n.setEmpresa(r.getEmpresa());
            n.setProducto(item.getProducto());
            n.setVariante(item.getVariante());
            n.setUom(item.getUom());
            n.setQty(faltante);
            String motivo = sugerir.apply(item, faltante);
            n.setSuggestedReason(motivo);
            if (motivo == null) {
                agregarAConsolidado(n);
            } else {
                n.setStatus(NecesidadCompra.PENDIENTE_RUTA);
            }
            necesidadRepository.save(n);
        }
    }

    /** Suma la necesidad al consolidado ABIERTO de su empresa + SKU + unidad (lo crea si no hay). */
    private void agregarAConsolidado(NecesidadCompra n) {
        UUID id = consolidadoAbierto(n);
        jdbc.update("update consolidacion set total_qty = total_qty + ? where consolidation_id = ?", n.getQty(), id);
        n.setConsolidacion(consolidacionRepository.getReferenceById(id));
        n.setStatus(NecesidadCompra.COMPRA);
    }

    /**
     * El indice unico parcial uq_consolidacion_abierta garantiza uno solo por
     * clave; "on conflict do nothing" + "for update" lo hace seguro ante dos
     * aprobaciones simultaneas.
     */
    private UUID consolidadoAbierto(NecesidadCompra n) {
        UUID variantId = n.getVariante() != null ? n.getVariante().getVariantId() : null;
        UUID companyId = n.getEmpresa().getCompanyId();
        UUID productId = n.getProducto().getProductId();
        UUID uomId = n.getUom().getUomId();
        jdbc.update("""
                insert into consolidacion (consolidation_id, consolidation_code, company_id, product_id, variant_id,
                  uom_id, total_qty, status)
                values (?, ?, ?, ?, cast(? as uuid), ?, 0, 'ABIERTA')
                on conflict (company_id, product_id, (coalesce(variant_id, '00000000-0000-0000-0000-000000000000'::uuid)), uom_id)
                  where status = 'ABIERTA' do nothing""",
                UUID.randomUUID(), CodigoCompra.generar("CON", n.getEmpresa().getCode()), companyId, productId,
                variantId, uomId);
        return jdbc.queryForObject("""
                select consolidation_id from consolidacion
                where company_id = ? and product_id = ? and variant_id is not distinct from cast(? as uuid)
                  and uom_id = ? and status = 'ABIERTA' for update""",
                UUID.class, companyId, productId, variantId, uomId);
    }

    /** Por decidir ruta y en Caja Chica con algo aun por comprar. */
    public List<NecesidadDto> listarNecesidades(UsuarioPrincipal principal) {
        exigirLogistica(principal);
        List<NecesidadCompra> needs = necesidadRepository.findByStatusIn(
                List.of(NecesidadCompra.PENDIENTE_RUTA, NecesidadCompra.CAJA_CHICA));
        Map<UUID, BigDecimal> comprado = compradoPorCajaChica(needs.stream().map(NecesidadCompra::getNeedId).toList());
        List<NecesidadDto> out = new ArrayList<>();
        for (NecesidadCompra n : needs) {
            BigDecimal resta = n.getQty().subtract(comprado.getOrDefault(n.getNeedId(), BigDecimal.ZERO));
            if (resta.signum() > 0) {
                out.add(ComprasMapper.toDto(n, resta));
            }
        }
        return out;
    }

    private Map<UUID, BigDecimal> compradoPorCajaChica(Collection<UUID> needIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (needIds.isEmpty()) {
            return out;
        }
        named.query("""
                select ci.need_id, sum(ci.quantity) from caja_chica_item ci
                join caja_chica_ref f on f.petty_cash_ref_id = ci.petty_cash_ref_id
                where ci.need_id in (:ids) and f.regularization_status <> 'ANULADO' group by ci.need_id""",
                new MapSqlParameterSource("ids", needIds),
                rs -> {
                    out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
                });
        return out;
    }

    /**
     * PENDIENTE_RUTA -> COMPRA | CAJA_CHICA; COMPRA (consolidado aun ABIERTO) ->
     * CAJA_CHICA; CAJA_CHICA (sin compras registradas) -> COMPRA.
     */
    @Transactional
    public NecesidadDto decidirRuta(UUID needId, String ruta, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        String destino = ruta == null ? "" : ruta.trim().toUpperCase(Locale.ROOT);
        if (!RUTAS.contains(destino)) {
            throw ApiException.badRequest("Ruta invalida.");
        }
        NecesidadCompra n = necesidadRepository.bloquear(needId)
                .orElseThrow(() -> ApiException.notFound("Necesidad no encontrada."));
        String anterior = n.getStatus();
        if (anterior.equals(destino)) {
            throw ApiException.badRequest("La necesidad ya esta en esa ruta.");
        }
        switch (anterior) {
            case NecesidadCompra.PENDIENTE_RUTA -> {
                if (NecesidadCompra.COMPRA.equals(destino)) {
                    agregarAConsolidado(n);
                } else {
                    n.setStatus(NecesidadCompra.CAJA_CHICA);
                }
            }
            case NecesidadCompra.COMPRA -> {
                Consolidacion c = consolidacionRepository.bloquear(n.getConsolidacion().getConsolidationId()).orElseThrow();
                if (!Consolidacion.ABIERTA.equals(c.getStatus())) {
                    throw ApiException.badRequest("El consolidado " + c.getConsolidationCode()
                            + " ya tiene solicitud de cotizacion: anulalo para cambiar la ruta.");
                }
                c.setTotalQty(c.getTotalQty().subtract(n.getQty()));
                n.setConsolidacion(null);
                n.setStatus(NecesidadCompra.CAJA_CHICA);
                necesidadRepository.saveAndFlush(n);
                if (c.getTotalQty().signum() <= 0) {
                    consolidacionRepository.delete(c);
                }
            }
            case NecesidadCompra.CAJA_CHICA -> {
                if (compradoPorCajaChica(List.of(needId)).getOrDefault(needId, BigDecimal.ZERO).signum() > 0) {
                    throw ApiException.badRequest("Ya hay compras por Caja Chica para esta necesidad.");
                }
                agregarAConsolidado(n);
            }
            default -> throw ApiException.badRequest("La necesidad ya no esta vigente.");
        }
        n.setRouteDecidedBy(principal.userId());
        n.setRouteDecidedAt(Instant.now());
        necesidadRepository.save(n);
        auditoria.registrar("NECESIDAD_COMPRA", needId, "DECIDIR_RUTA", Map.of("estado", anterior),
                Map.of("estado", n.getStatus()), principal.userId());
        return ComprasMapper.toDto(n, n.getQty());
    }

    // ================================================================ consolidados

    public List<ConsolidadoDto> listarConsolidados(UsuarioPrincipal principal) {
        exigirLogistica(principal);
        return armar(consolidacionRepository.findByStatusIn(CONSOLIDADOS_VIGENTES));
    }

    private List<ConsolidadoDto> armar(List<Consolidacion> cs) {
        if (cs.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = cs.stream().map(Consolidacion::getConsolidationId).toList();
        Map<UUID, List<OrigenDto>> origenes = new HashMap<>();
        for (NecesidadCompra n : necesidadRepository.findByConsolidaciones(ids)) {
            origenes.computeIfAbsent(n.getConsolidacion().getConsolidationId(), k -> new ArrayList<>())
                    .add(ComprasMapper.toOrigen(n));
        }
        Map<UUID, List<String>> proveedores = proveedoresHabilitados(
                cs.stream().map(c -> c.getProducto().getProductId()).collect(Collectors.toSet()));
        // Ofertas vigentes y la mayor cantidad ofertada (no la suma: varios proveedores
        // suelen cotizar el total y sumar sobrecontaria).
        Map<UUID, Integer> cotizaciones = new HashMap<>();
        Map<UUID, BigDecimal> cotizado = new HashMap<>();
        named.query("""
                select ri.consolidation_id, count(*), max(ci.qty) from cotizacion_item ci
                join rfq_item ri on ri.rfq_item_id = ci.rfq_item_id
                join cotizacion c on c.quotation_id = ci.quotation_id
                where ri.consolidation_id in (:ids) and c.status <> 'DESCARTADA' group by ri.consolidation_id""",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    cotizaciones.put(rs.getObject(1, UUID.class), rs.getInt(2));
                    cotizado.put(rs.getObject(1, UUID.class), rs.getBigDecimal(3));
                });
        return cs.stream().map(c -> ComprasMapper.toDto(c,
                        origenes.getOrDefault(c.getConsolidationId(), List.of()),
                        proveedores.getOrDefault(c.getProducto().getProductId(), List.of()),
                        cotizaciones.getOrDefault(c.getConsolidationId(), 0),
                        cotizado.get(c.getConsolidationId())))
                .toList();
    }

    private Map<UUID, List<String>> proveedoresHabilitados(Collection<UUID> productIds) {
        Map<UUID, List<String>> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        named.query("""
                select pp.product_id, p.legal_name from proveedor_producto pp
                join proveedor p on p.supplier_id = pp.supplier_id
                where p.active and pp.product_id in (:ids) order by p.legal_name""",
                new MapSqlParameterSource("ids", productIds),
                rs -> {
                    out.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return out;
    }

    public ConsolidadoDetalleDto obtenerConsolidado(UUID id, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Consolidacion c = consolidacionRepository.findDetalleById(id)
                .orElseThrow(() -> ApiException.notFound("Consolidado no encontrado."));
        ConsolidadoDto dto = armar(List.of(c)).get(0);

        List<RfqItem> lineas = rfqItemRepository.findByConsolidacion(id);
        Map<UUID, List<CotizacionItem>> porLinea = new HashMap<>();
        if (!lineas.isEmpty()) {
            for (CotizacionItem ci : cotizacionItemRepository.findByRfqItems(
                    lineas.stream().map(RfqItem::getRfqItemId).toList())) {
                porLinea.computeIfAbsent(ci.getRfqItem().getRfqItemId(), k -> new ArrayList<>()).add(ci);
            }
        }
        UUID adjudicado = c.getAdjudicado() != null ? c.getAdjudicado().getQuotationItemId() : null;
        List<OfertaDto> ofertas = new ArrayList<>();
        for (RfqItem li : lineas) {
            Rfq q = li.getRfq();
            List<CotizacionItem> cotizados = porLinea.getOrDefault(li.getRfqItemId(), List.of());
            if (cotizados.isEmpty()) {
                ofertas.add(new OfertaDto(q.getRfqId(), q.getRfqCode(), q.getStatus(), q.getProveedor().getLegalName(),
                        null, null, null, null, null, li.getQty(), null, null, null, null, null, null, null, null, false));
            }
            for (CotizacionItem ci : cotizados) {
                Cotizacion cot = ci.getCotizacion();
                ofertas.add(new OfertaDto(q.getRfqId(), q.getRfqCode(), q.getStatus(), q.getProveedor().getLegalName(),
                        cot.getQuotationId(), ci.getQuotationItemId(), cot.getSupplierQuoteNumber(), cot.getStatus(),
                        cot.getMoneda().getCurrencyCode(), ci.getQty(), ci.getUnitCost(), ci.getLineTotal(),
                        cot.getPaymentCondition(), cot.getDeliveryMode(), cot.getLeadDays(), ci.getOfferedBrand(),
                        ci.getOfferedModel(), cot.getDocumento() != null ? cot.getDocumento().getFileUrl() : null,
                        ci.getQuotationItemId().equals(adjudicado)));
            }
        }
        // Cotizadas primero, de menor a mayor costo unitario (por moneda); luego las que no respondieron.
        ofertas.sort(Comparator.comparing((OfertaDto o) -> o.unitCost() == null)
                .thenComparing(o -> o.currencyCode() != null ? o.currencyCode() : "")
                .thenComparing(o -> o.unitCost() != null ? o.unitCost() : BigDecimal.ZERO));

        BigDecimal ultimo = c.getVariante() != null ? cajaChicaService.ultimoCosto(c.getVariante().getVariantId()) : null;
        List<Map<String, Object>> oc = jdbc.queryForList("""
                select o.order_id, o.order_code from orden_item oi join orden_abastecimiento o on o.order_id = oi.order_id
                where oi.consolidation_id = ? and o.status <> 'ANULADA' order by o.created_at desc limit 1""", id);
        return new ConsolidadoDetalleDto(dto, ofertas, ultimo,
                oc.isEmpty() ? null : (UUID) oc.get(0).get("order_id"),
                oc.isEmpty() ? null : (String) oc.get(0).get("order_code"));
    }

    /**
     * Cierra los consolidados elegidos y arma una RFQ por empresa + proveedor
     * habilitado con todos sus productos (si ya hay una preparada sin enviar,
     * se le agregan las lineas).
     */
    @Transactional
    public List<RfqDto> prepararSolicitudes(List<UUID> consolidationIds, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        // Un bloqueo para todos (ordenado por id); se conserva el orden en que se eligieron.
        LinkedHashSet<UUID> elegidos = new LinkedHashSet<>(consolidationIds);
        Map<UUID, Consolidacion> bloqueados = consolidacionRepository.bloquearVarios(elegidos).stream()
                .collect(Collectors.toMap(Consolidacion::getConsolidationId, c -> c));
        List<Consolidacion> cs = new ArrayList<>();
        for (UUID id : elegidos) {
            Consolidacion c = bloqueados.get(id);
            if (c == null) {
                throw ApiException.notFound("Consolidado no encontrado.");
            }
            if (!Consolidacion.ABIERTA.equals(c.getStatus())) {
                throw ApiException.badRequest("El consolidado " + c.getConsolidationCode() + " ya tiene solicitud de cotizacion.");
            }
            cs.add(c);
        }
        Map<UUID, List<Proveedor>> cobertura = cobertura(cs.stream().map(c -> c.getProducto().getProductId()).toList());
        List<String> sinProveedor = cs.stream()
                .filter(c -> cobertura.getOrDefault(c.getProducto().getProductId(), List.of()).isEmpty())
                .map(c -> c.getProducto().getSkuBase()).distinct().toList();
        if (!sinProveedor.isEmpty()) {
            throw ApiException.badRequest("Sin proveedor habilitado: " + String.join(", ", sinProveedor)
                    + ". Asignalo en el Catalogo de productos.");
        }
        Map<UUID, String> especificaciones = especificaciones(cs.stream().map(Consolidacion::getConsolidationId).toList());

        Map<String, Rfq> rfqs = new LinkedHashMap<>();
        for (Consolidacion c : cs) {
            for (Proveedor p : cobertura.get(c.getProducto().getProductId())) {
                Rfq rfq = rfqs.computeIfAbsent(c.getEmpresa().getCompanyId() + "|" + p.getSupplierId(),
                        k -> rfqRepository.findBorradores(c.getEmpresa().getCompanyId(), p.getSupplierId()).stream()
                                .findFirst().orElseGet(() -> nuevaRfq(c.getEmpresa(), p, usuario)));
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
            }
            c.setStatus(Consolidacion.EN_COTIZACION);
            auditoria.registrar("CONSOLIDACION", c.getConsolidationId(), "PREPARAR_RFQ",
                    Map.of("estado", Consolidacion.ABIERTA), Map.of("estado", Consolidacion.EN_COTIZACION,
                            "total", c.getTotalQty()), principal.userId());
        }
        List<Rfq> tocadas = List.copyOf(rfqs.values());
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origenDespuesDeGuardar(tocadas);
        return tocadas.stream().map(r -> ComprasMapper.toDto(r, refs.getOrDefault(r.getRfqId(), List.of()))).toList();
    }

    private Map<UUID, List<OrigenCompras.ReqRef>> origenDespuesDeGuardar(List<Rfq> rfqs) {
        rfqItemRepository.flush();
        return origen.deRfqs(rfqs.stream().map(Rfq::getRfqId).toList());
    }

    public Rfq nuevaRfq(Empresa empresa, Proveedor proveedor, Usuario usuario) {
        Rfq rfq = new Rfq();
        rfq.setRfqId(UUID.randomUUID());
        rfq.setRfqCode(CodigoCompra.generar("RFQ", empresa.getCode()));
        rfq.setEmpresa(empresa);
        rfq.setProveedor(proveedor);
        rfq.setIssueDate(LocalDate.now());
        rfq.setStatus("BORRADOR");
        rfq.setCreatedBy(usuario);
        return rfqRepository.save(rfq);
    }

    /** Proveedores activos habilitados por producto. */
    private Map<UUID, List<Proveedor>> cobertura(Collection<UUID> productIds) {
        Map<UUID, List<UUID>> ids = new HashMap<>();
        named.query("""
                select pp.product_id, pp.supplier_id from proveedor_producto pp
                join proveedor p on p.supplier_id = pp.supplier_id
                where p.active and pp.product_id in (:ids) order by p.legal_name""",
                new MapSqlParameterSource("ids", productIds),
                rs -> {
                    ids.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
                });
        Map<UUID, Proveedor> proveedores = proveedorRepository.findAllById(
                        ids.values().stream().flatMap(List::stream).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Proveedor::getSupplierId, p -> p));
        Map<UUID, List<Proveedor>> out = new HashMap<>();
        ids.forEach((producto, lista) -> out.put(producto, lista.stream().map(proveedores::get).toList()));
        return out;
    }

    /** Requisitos tecnicos de los items de origen, sin repetir. */
    public Map<UUID, String> especificaciones(List<UUID> consolidationIds) {
        Map<UUID, Set<String>> textos = new HashMap<>();
        for (NecesidadCompra n : necesidadRepository.findByConsolidaciones(consolidationIds)) {
            String t = n.getRequerimientoItem().getTechnicalRequirement();
            if (t != null && !t.isBlank()) {
                textos.computeIfAbsent(n.getConsolidacion().getConsolidationId(), k -> new LinkedHashSet<>()).add(t.trim());
            }
        }
        Map<UUID, String> out = new HashMap<>();
        textos.forEach((k, v) -> out.put(k, String.join("; ", v)));
        return out;
    }

    @Transactional
    public RfqDto confirmarEnvio(UUID rfqId, String canal, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        String c = canal == null ? "" : canal.trim().toUpperCase(Locale.ROOT);
        if (!CANALES.contains(c)) {
            throw ApiException.badRequest("Canal de envio invalido.");
        }
        Rfq rfq = rfqRepository.findDetalleById(rfqId).orElseThrow(() -> ApiException.notFound("RFQ no encontrada."));
        if (!"BORRADOR".equals(rfq.getStatus())) {
            throw ApiException.badRequest("Esta RFQ ya fue enviada.");
        }
        rfq.setStatus("ENVIADA");
        rfq.setIssueDate(LocalDate.now());
        rfq.setSentAt(Instant.now());
        rfq.setSentBy(principal.userId());
        rfq.setSentChannel(c);
        rfqRepository.save(rfq);
        auditoria.registrar("RFQ", rfqId, "ENVIAR", Map.of("estado", "BORRADOR"),
                Map.of("estado", "ENVIADA", "canal", c), principal.userId());
        return ComprasMapper.toDto(rfq, origen.deRfq(rfqId));
    }

    // ================================================================ adjudicacion y OC

    @Transactional
    public ConsolidadoDetalleDto adjudicar(UUID consolidationId, UUID quotationItemId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        adjudicarLinea(consolidationId, quotationItemId, principal);
        return obtenerConsolidado(consolidationId, principal);
    }

    /** Adjudica sin volver a leer el detalle (el comparativo adjudica varios consolidados de una vez). */
    @Transactional
    public void adjudicarLinea(UUID consolidationId, UUID quotationItemId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Consolidacion c = consolidacionRepository.bloquear(consolidationId)
                .orElseThrow(() -> ApiException.notFound("Consolidado no encontrado."));
        if (!Consolidacion.EN_COTIZACION.equals(c.getStatus()) && !Consolidacion.ADJUDICADA.equals(c.getStatus())) {
            throw ApiException.badRequest("El consolidado no esta en cotizacion (estado " + c.getStatus() + ").");
        }
        CotizacionItem ci = cotizacionItemRepository.findById(quotationItemId)
                .orElseThrow(() -> ApiException.notFound("Oferta no encontrada."));
        Consolidacion de = ci.getRfqItem().getConsolidacion();
        if (de == null || !de.getConsolidationId().equals(consolidationId)) {
            throw ApiException.badRequest("La oferta no corresponde a este consolidado.");
        }
        if ("DESCARTADA".equals(ci.getCotizacion().getStatus())) {
            throw ApiException.badRequest("La cotizacion fue descartada.");
        }
        Map<String, Object> antes = new HashMap<>();
        antes.put("estado", c.getStatus());
        if (c.getAdjudicado() != null) {
            antes.put("oferta", c.getAdjudicado().getQuotationItemId());
        }
        c.setAdjudicado(ci);
        c.setStatus(Consolidacion.ADJUDICADA);
        c.setAwardedBy(principal.userId());
        c.setAwardedAt(Instant.now());
        consolidacionRepository.save(c);
        auditoria.registrar("CONSOLIDACION", consolidationId, "ADJUDICAR", antes,
                Map.of("estado", Consolidacion.ADJUDICADA, "oferta", quotationItemId,
                        "proveedor", ci.getCotizacion().getProveedor().getLegalName()), principal.userId());
        consolidacionRepository.flush();
    }

    /** Adjudicados sin OC agrupados por cotizacion (= por proveedor y sus condiciones). */
    public List<PorEmitirDto> listarPorEmitir(UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Map<UUID, List<Consolidacion>> porCotizacion = new LinkedHashMap<>();
        for (Consolidacion c : consolidacionRepository.findByStatusIn(List.of(Consolidacion.ADJUDICADA))) {
            porCotizacion.computeIfAbsent(c.getAdjudicado().getCotizacion().getQuotationId(), k -> new ArrayList<>()).add(c);
        }
        List<PorEmitirDto> out = new ArrayList<>();
        porCotizacion.forEach((qid, cs) -> {
            Cotizacion q = cs.get(0).getAdjudicado().getCotizacion();
            List<PorEmitirDto.Linea> lineas = cs.stream().map(c -> {
                CotizacionItem ci = c.getAdjudicado();
                return new PorEmitirDto.Linea(c.getConsolidationId(), c.getConsolidationCode(),
                        c.getProducto().getSkuBase(),
                        c.getVariante() != null ? c.getVariante().getInternalVariantCode() : null,
                        c.getProducto().getName(), c.getUom().getCode(), ci.getQty(), ci.getUnitCost(), ci.getLineTotal());
            }).toList();
            BigDecimal total = lineas.stream().map(PorEmitirDto.Linea::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new PorEmitirDto(qid, q.getSupplierQuoteNumber(), q.getProveedor().getLegalName(),
                    cs.get(0).getEmpresa().getCode(), q.getMoneda().getCurrencyCode(), q.getPaymentCondition(), total,
                    lineas));
        });
        return out;
    }

    /**
     * Una OC con todos los consolidados adjudicados a lineas de la cotizacion.
     * El proveedor queda habilitado para esos productos (reemplaza "la primera
     * compra fija el proveedor habitual", DEC-L018).
     */
    @Transactional
    public com.tdem.logistica.modules.compras.dto.OrdenDto emitirOrden(UUID quotationId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        Cotizacion cot = cotizacionRepository.findDetalleById(quotationId)
                .orElseThrow(() -> ApiException.notFound("Cotizacion no encontrada."));
        if ("DESCARTADA".equals(cot.getStatus())) {
            throw ApiException.badRequest("La cotizacion fue descartada.");
        }
        List<Consolidacion> cs = consolidacionRepository.bloquearAdjudicadasDe(quotationId);
        if (cs.isEmpty()) {
            throw ApiException.badRequest("No hay productos adjudicados a esta cotizacion pendientes de OC.");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        Rfq rfq = cot.getRfq();

        OrdenAbastecimiento orden = new OrdenAbastecimiento();
        orden.setOrderId(UUID.randomUUID());
        orden.setOrderCode(CodigoCompra.generar("OC", rfq.getEmpresa().getCode()));
        orden.setEmpresa(rfq.getEmpresa());
        orden.setProveedor(cot.getProveedor());
        orden.setOrderType("OC_NORMAL");
        orden.setCotizacion(cot);
        orden.setIssueDate(LocalDate.now());
        orden.setPaymentCondition(cot.getPaymentCondition());
        orden.setDeliveryMode(cot.getDeliveryMode());
        orden.setDeliveryLocation(rfq.getDeliveryLocation());
        orden.setMoneda(cot.getMoneda());
        orden.setTaxAmount(BigDecimal.ZERO);
        orden.setStatus("EMITIDA");
        orden.setCreatedBy(usuario);
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrdenItem> items = new ArrayList<>();
        for (Consolidacion c : cs) {
            CotizacionItem ci = c.getAdjudicado();
            String atributo = ci.getVariante() != null ? " (" + ci.getVariante().getAttributeValue() + ")" : "";
            OrdenItem oi = new OrdenItem();
            oi.setOrderItemId(UUID.randomUUID());
            oi.setOrden(orden);
            oi.setConsolidacion(c);
            oi.setProducto(ci.getProducto());
            oi.setVariante(ci.getVariante());
            oi.setDescription(ci.getProducto().getName() + atributo);
            oi.setUom(c.getUom());
            oi.setQtyOrdered(ci.getQty());
            oi.setUnitCost(ci.getUnitCost());
            oi.setLineSubtotal(ci.getLineTotal());
            oi.setTaxAmount(BigDecimal.ZERO);
            oi.setLineTotal(ci.getLineTotal());
            items.add(oi);
            subtotal = subtotal.add(ci.getLineTotal());
            c.setStatus(Consolidacion.ORDENADA);
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        orden.setSubtotal(subtotal);
        orden.setTotal(subtotal);
        ordenRepository.save(orden);
        ordenItemRepository.saveAll(items);
        cot.setStatus("SELECCIONADA");
        cotizacionRepository.save(cot);

        for (Consolidacion c : cs) {
            jdbc.update("""
                    insert into proveedor_producto (product_id, supplier_id, created_by) values (?, ?, ?)
                    on conflict do nothing""", c.getProducto().getProductId(), cot.getProveedor().getSupplierId(),
                    principal.userId());
        }
        List<UUID> ids = cs.stream().map(Consolidacion::getConsolidationId).toList();
        cerrarRfqsResueltas(ids);
        auditoria.registrar("ORDEN_ABASTECIMIENTO", orden.getOrderId(), "EMITIR", null,
                Map.of("orden", orden.getOrderCode(), "cotizacion", quotationId, "consolidados", ids.size()),
                principal.userId());
        return ComprasMapper.toDto(orden, origen.deOrden(orden.getOrderId()));
    }

    /** RFQ cuyas lineas ya estan todas ordenadas o anuladas: no esperan mas respuestas. */
    void cerrarRfqsResueltas(Collection<UUID> consolidationIds) {
        consolidacionRepository.flush();
        named.update("""
                update rfq q set status = 'CERRADA'
                where q.status <> 'CERRADA'
                  and q.rfq_id in (select ri.rfq_id from rfq_item ri where ri.consolidation_id in (:ids))
                  and not exists (select 1 from rfq_item ri join consolidacion c on c.consolidation_id = ri.consolidation_id
                                  where ri.rfq_id = q.rfq_id and c.status not in ('ORDENADA','ANULADA'))""",
                new MapSqlParameterSource("ids", consolidationIds));
    }

    /**
     * El proveedor no atendio (RF-025): el consolidado se anula y cada faltante
     * vuelve a "por decidir ruta" sugiriendo Caja Chica por falla de proveedor.
     */
    @Transactional
    public ConsolidadoDetalleDto anularConsolidado(UUID id, String motivo, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Indica el motivo para anular el consolidado.");
        }
        Consolidacion c = consolidacionRepository.bloquear(id)
                .orElseThrow(() -> ApiException.notFound("Consolidado no encontrado."));
        if (!Consolidacion.EN_COTIZACION.equals(c.getStatus()) && !Consolidacion.ADJUDICADA.equals(c.getStatus())) {
            throw ApiException.badRequest(Consolidacion.ORDENADA.equals(c.getStatus())
                    ? "El consolidado tiene OC: anula primero la orden de compra."
                    : "Solo se anula un consolidado en cotizacion.");
        }
        String anterior = c.getStatus();
        c.setStatus(Consolidacion.ANULADA);
        c.setAdjudicado(null);
        c.setCancelReason(motivo.trim());
        for (NecesidadCompra n : necesidadRepository.findByConsolidaciones(List.of(id))) {
            n.setStatus(NecesidadCompra.ANULADA);
            NecesidadCompra nueva = new NecesidadCompra();
            nueva.setNeedId(UUID.randomUUID());
            nueva.setRequerimientoItem(n.getRequerimientoItem());
            nueva.setEmpresa(n.getEmpresa());
            nueva.setProducto(n.getProducto());
            nueva.setVariante(n.getVariante());
            nueva.setUom(n.getUom());
            nueva.setQty(n.getQty());
            nueva.setStatus(NecesidadCompra.PENDIENTE_RUTA);
            nueva.setSuggestedReason("FALLA_PROVEEDOR");
            necesidadRepository.save(nueva);
        }
        necesidadRepository.flush();
        cerrarRfqsResueltas(List.of(id));
        auditoria.registrar("CONSOLIDACION", id, "ANULAR", Map.of("estado", anterior),
                Map.of("estado", Consolidacion.ANULADA, "motivo", motivo.trim()), principal.userId());
        return obtenerConsolidado(id, principal);
    }

    // ================================================================ cobertura proveedor <-> producto

    public List<ProveedorProductoDto> listarCobertura(UsuarioPrincipal principal) {
        exigirLogistica(principal);
        return jdbc.query("""
                select pp.product_id, pp.supplier_id, p.legal_name from proveedor_producto pp
                join proveedor p on p.supplier_id = pp.supplier_id order by p.legal_name""",
                (rs, i) -> new ProveedorProductoDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3)));
    }

    @Transactional
    public List<ProveedorProductoDto> reemplazarCobertura(UUID productId, List<UUID> supplierIds,
                                                          UsuarioPrincipal principal) {
        exigirLogistica(principal);
        if (!productoRepository.existsById(productId)) {
            throw ApiException.notFound("Producto no encontrado.");
        }
        Set<UUID> nuevos = supplierIds == null ? Set.of() : new LinkedHashSet<>(supplierIds);
        Set<UUID> actuales = new HashSet<>(jdbc.queryForList(
                "select supplier_id from proveedor_producto where product_id = ?", UUID.class, productId));
        // Los que se agregan deben estar activos: una consulta para todos.
        List<UUID> agregados = nuevos.stream().filter(s -> !actuales.contains(s)).toList();
        if (!agregados.isEmpty()) {
            long activos = proveedorRepository.findAllById(agregados).stream().filter(Proveedor::isActive).count();
            if (activos != agregados.size()) {
                throw ApiException.badRequest("Selecciona proveedores activos.");
            }
        }
        for (UUID s : actuales) {
            if (!nuevos.contains(s)) {
                jdbc.update("delete from proveedor_producto where product_id = ? and supplier_id = ?", productId, s);
            }
        }
        for (UUID s : nuevos) {
            if (!actuales.contains(s)) {
                jdbc.update("insert into proveedor_producto (product_id, supplier_id, created_by) values (?, ?, ?)",
                        productId, s, principal.userId());
            }
        }
        auditoria.registrar("PRODUCTO", productId, "PROVEEDORES", Map.of("proveedores", actuales),
                Map.of("proveedores", nuevos), principal.userId());
        return jdbc.query("""
                select pp.product_id, pp.supplier_id, p.legal_name from proveedor_producto pp
                join proveedor p on p.supplier_id = pp.supplier_id where pp.product_id = ? order by p.legal_name""",
                (rs, i) -> new ProveedorProductoDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3)), productId);
    }
}
