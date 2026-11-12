package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.almacen.AtencionCalculator;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.PrioridadRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.cajachica.dto.*;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Caja Chica como ruta de compra del requerimiento (DEC-L003, DEC-L026).
 * <ul>
 *   <li>Sugerencia por regla (RF-023..025, RF-051): bajo monto (pendiente
 *       valorizado al ultimo costo conocido <= umbral), mismo dia (urgente o
 *       critico con fecha requerida hoy o vencida) y falla de proveedor (RFQ
 *       cerrada sin OC).</li>
 *   <li>Caja chica del proyecto (DEC-L054): el solicitante la sugiere,
 *       Logistica autoriza la ruta en Necesidades y el proyecto compra con su
 *       fondo. El solicitante registra la compra con su comprobante; lo
 *       comprado se queda en el proyecto (no entra al almacen) y cuenta como
 *       atendido cuando Logistica regulariza. El comprobante no puede repetirse.</li>
 *   <li>Regularizacion: PENDIENTE_REGULARIZACION -> REGULARIZADO (con el id del
 *       movimiento en el aplicativo de Caja Chica) u OBSERVADO con motivo.</li>
 * </ul>
 * Solo REFERENCIA el aplicativo externo de Caja Chica; nunca duplica su fondo.
 */
@Service
@Transactional(readOnly = true)
public class CajaChicaService {

    public static final String BAJO_MONTO = "BAJO_MONTO";
    public static final String MISMO_DIA = "MISMO_DIA";
    public static final String FALLA_PROVEEDOR = "FALLA_PROVEEDOR";
    public static final String EXCEPCION = "EXCEPCION_AUTORIZADA";
    /** DEC-L054: el solicitante sugirio la caja chica del proyecto. */
    public static final String SOLICITANTE = "SOLICITANTE";
    private static final Set<String> TIPOS_COMPROBANTE = Set.of("FACTURA", "BOLETA", "RECIBO", "TICKET", "OTRO");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate jdbc;
    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final EmpresaRepository empresaRepository;
    private final Auditoria auditoria;
    private final RealtimeNotifier realtimeNotifier;
    private final AtencionCalculator atencionCalculator;

    public CajaChicaService(JdbcTemplate jdbc, RequerimientoRepository requerimientoRepository,
                            RequerimientoItemRepository requerimientoItemRepository, EmpresaRepository empresaRepository,
                            Auditoria auditoria, RealtimeNotifier realtimeNotifier, AtencionCalculator atencionCalculator) {
        this.jdbc = jdbc;
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.empresaRepository = empresaRepository;
        this.auditoria = auditoria;
        this.realtimeNotifier = realtimeNotifier;
        this.atencionCalculator = atencionCalculator;
    }

    private static void exigirGestor(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("Solo Logistica puede gestionar Caja Chica.");
        }
    }

    // ---------------------------------------------------------------- regla (RF-026)

    /** Regla vigente de la empresa o, si no tiene, la corporativa. */
    public ReglaCajaChicaDto regla(UUID companyId) {
        List<ReglaCajaChicaDto> r = jdbc.query("""
                select r.petty_cash_rule_id, e.code, r.low_amount_threshold, r.threshold_currency, r.low_amount_enabled,
                  r.same_day_enabled, r.supplier_failure_enabled, r.manual_exception_enabled, r.updated_at
                from regla_caja_chica r left join empresa e on e.company_id = r.company_id
                where r.status = 'VIGENTE' and (r.company_id = ? or r.company_id is null)
                order by r.company_id nulls last limit 1""", (rs, i) -> regla(rs), companyId);
        if (r.isEmpty()) {
            throw ApiException.badRequest("No hay una regla de Caja Chica vigente.");
        }
        return r.get(0);
    }

    public ReglaCajaChicaDto reglaCorporativa(UsuarioPrincipal principal) {
        exigirGestor(principal);
        return regla((UUID) null);
    }

    /** Solo ADMIN edita el lineamiento corporativo (PD-05 sigue siendo referencial). */
    @Transactional
    public ReglaCajaChicaDto guardarRegla(GuardarReglaRequest r, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.ADMIN)) {
            throw ApiException.forbidden("Solo un administrador puede cambiar la regla de Caja Chica.");
        }
        int n = jdbc.update("""
                update regla_caja_chica set low_amount_threshold = ?, low_amount_enabled = ?, same_day_enabled = ?,
                  supplier_failure_enabled = ?, manual_exception_enabled = ?, updated_by = ?, updated_at = now()
                where company_id is null and status = 'VIGENTE'""",
                r.umbral().setScale(2, RoundingMode.HALF_UP), r.bajoMonto(), r.mismoDia(), r.fallaProveedor(),
                r.excepcion(), principal.userId());
        if (n == 0) {
            throw ApiException.badRequest("No hay una regla corporativa vigente.");
        }
        return regla((UUID) null);
    }

    private static ReglaCajaChicaDto regla(ResultSet rs) throws SQLException {
        Timestamp t = rs.getTimestamp(9);
        return new ReglaCajaChicaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8), t != null ? t.toInstant() : null);
    }

    // ---------------------------------------------------------------- sugerencia

    public EvaluacionCajaChicaDto evaluar(UUID requestId, UsuarioPrincipal principal) {
        exigirGestor(principal);
        Requerimiento r = requerimiento(requestId);
        ReglaCajaChicaDto regla = regla(r.getEmpresa().getCompanyId());
        List<EvaluacionCajaChicaDto.Motivo> motivos = new ArrayList<>();

        BigDecimal estimado = BigDecimal.ZERO;
        Map<UUID, BigDecimal> pendientes = pendientesCajaChica(requestId);
        for (RequerimientoItem item : requerimientoItemRepository.findByRequerimiento(requestId)) {
            BigDecimal pendiente = pendientes.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO);
            if (pendiente.signum() <= 0) {
                continue;
            }
            BigDecimal costo = item.getVariante() != null ? ultimoCosto(item.getVariante().getVariantId()) : null;
            if (costo == null) {
                estimado = null;
                break;
            }
            estimado = estimado.add(costo.multiply(pendiente));
        }
        if (estimado != null) {
            estimado = estimado.setScale(2, RoundingMode.HALF_UP);
        }
        if (regla.bajoMonto() && estimado != null && estimado.signum() > 0 && estimado.compareTo(regla.umbral()) <= 0) {
            motivos.add(new EvaluacionCajaChicaDto.Motivo(BAJO_MONTO,
                    "Pendiente estimado " + regla.moneda() + " " + estimado.toPlainString() + " (umbral " + regla.umbral().toPlainString() + ")"));
        }
        if (regla.mismoDia() && esMismoDia(r)) {
            motivos.add(new EvaluacionCajaChicaDto.Motivo(MISMO_DIA, "Prioridad " + r.getPriority().toLowerCase(Locale.ROOT)
                    + " para " + r.getRequiredDate()));
        }
        if (regla.fallaProveedor() && hayFallaProveedor(requestId)) {
            motivos.add(new EvaluacionCajaChicaDto.Motivo(FALLA_PROVEEDOR, "El proveedor no atendio la compra"));
        }
        return new EvaluacionCajaChicaDto(regla, estimado, motivos);
    }

    /**
     * Ruta sugerida para el faltante de cada item al aprobar (DEC-L039): MISMO_DIA
     * o BAJO_MONTO si la regla vigente lo habilita; null = compra normal. La regla
     * se lee una vez por requerimiento, no una vez por item.
     */
    public java.util.function.BiFunction<RequerimientoItem, BigDecimal, String> sugeridor(Requerimiento r) {
        ReglaCajaChicaDto regla;
        try {
            regla = regla(r.getEmpresa().getCompanyId());
        } catch (ApiException sinRegla) {
            return (item, faltante) -> null;
        }
        boolean mismoDia = regla.mismoDia() && esMismoDia(r);
        return (item, faltante) -> {
            if (mismoDia) {
                return MISMO_DIA;
            }
            if (!regla.bajoMonto() || item.getVariante() == null) {
                return null;
            }
            BigDecimal costo = ultimoCosto(item.getVariante().getVariantId());
            return costo != null && costo.multiply(faltante).compareTo(regla.umbral()) <= 0 ? BAJO_MONTO : null;
        };
    }

    /**
     * Lo que cada item puede comprar por Caja Chica: sus necesidades por decidir
     * ruta o en ruta Caja Chica, menos lo ya comprado contra ellas.
     */
    public Map<UUID, BigDecimal> pendientesCajaChica(UUID requestId) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                select n.request_item_id, sum(n.qty - coalesce((select sum(ci.quantity) from caja_chica_item ci
                    join caja_chica_ref f on f.petty_cash_ref_id = ci.petty_cash_ref_id
                    where ci.need_id = n.need_id and f.regularization_status <> 'ANULADO'), 0))
                from necesidad_compra n join requerimiento_item ri on ri.request_item_id = n.request_item_id
                where ri.request_id = ? and n.status in ('PENDIENTE_RUTA','CAJA_CHICA')
                group by n.request_item_id""", rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2).max(BigDecimal.ZERO));
        }, requestId);
        return out;
    }

    /** Ultimo costo unitario conocido de la variante: OC no anulada o compra por Caja Chica. */
    public BigDecimal ultimoCosto(UUID variantId) {
        List<BigDecimal> c = jdbc.queryForList("""
                select costo from (
                  select oi.unit_cost as costo, o.created_at as fecha from orden_item oi
                  join orden_abastecimiento o on o.order_id = oi.order_id
                  where oi.variant_id = ? and o.status <> 'ANULADA'
                  union all
                  select ci.unit_cost, r.created_at from caja_chica_item ci
                  join caja_chica_ref r on r.petty_cash_ref_id = ci.petty_cash_ref_id
                  where ci.variant_id = ? and r.regularization_status <> 'ANULADO'
                ) x order by fecha desc limit 1""", BigDecimal.class, variantId, variantId);
        return c.isEmpty() ? null : c.get(0);
    }

    private static boolean esMismoDia(Requerimiento r) {
        boolean urgente = PrioridadRequerimiento.URGENTE.equals(r.getPriority())
                || PrioridadRequerimiento.CRITICA.equals(r.getPriority());
        return urgente && r.getRequiredDate() != null && !r.getRequiredDate().isAfter(LocalDate.now(LIMA));
    }

    /** Un consolidado del REQ se anulo porque el proveedor no atendio (o, antes del Lote 16, RFQ cerrada sin OC). */
    private boolean hayFallaProveedor(UUID requestId) {
        Integer n = jdbc.queryForObject("""
                select (select count(*) from necesidad_compra n
                          join requerimiento_item ri on ri.request_item_id = n.request_item_id
                         where ri.request_id = ? and n.suggested_reason = 'FALLA_PROVEEDOR'
                           and n.status in ('PENDIENTE_RUTA','CAJA_CHICA'))
                     + (select count(*) from rfq q where q.request_id = ? and q.status = 'CERRADA'
                          and not exists (select 1 from cotizacion c join orden_abastecimiento o on o.quotation_id = c.quotation_id
                                          where c.rfq_id = q.rfq_id and o.status <> 'ANULADA'))""",
                Integer.class, requestId, requestId);
        return n != null && n > 0;
    }

    // ---------------------------------------------------------------- caja chica del proyecto (DEC-L054)

    /** El dueno del requerimiento o Logistica/Admin. */
    private Requerimiento requerimientoDeCompra(UUID requestId, UsuarioPrincipal principal) {
        Requerimiento r = requerimiento(requestId);
        if (!esGestor(principal) && !esDueno(r, principal)) {
            throw ApiException.forbidden("No tienes permiso para registrar compras de este requerimiento.");
        }
        return r;
    }

    private static boolean esGestor(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
    }

    private static boolean esDueno(Requerimiento r, UsuarioPrincipal p) {
        return r.getRequester() != null && r.getRequester().getUserId().equals(p.userId());
    }

    /** Valida que el usuario pueda subir el comprobante de este requerimiento. */
    public void exigirAccesoCompra(UUID requestId, UsuarioPrincipal principal) {
        requerimientoDeCompra(requestId, principal);
    }

    /**
     * Lo que Logistica autorizo comprar con caja chica (necesidades en ruta
     * CAJA_CHICA) menos lo ya registrado (no anulado), por item.
     */
    private Map<UUID, BigDecimal> autorizados(UUID requestId, UUID excluirRef) {
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        jdbc.query("""
                select n.request_item_id, sum(n.qty) - coalesce((select sum(ci.quantity) from caja_chica_item ci
                    join caja_chica_ref f on f.petty_cash_ref_id = ci.petty_cash_ref_id
                    where ci.request_item_id = n.request_item_id and f.regularization_status <> 'ANULADO'
                      and f.petty_cash_ref_id is distinct from cast(? as uuid)), 0)
                from necesidad_compra n join requerimiento_item ri on ri.request_item_id = n.request_item_id
                where ri.request_id = ? and n.status = 'CAJA_CHICA'
                group by n.request_item_id""", rs -> {
            out.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2).max(BigDecimal.ZERO));
        }, excluirRef, requestId);
        return out;
    }

    public FormularioCajaChicaDto formulario(UUID requestId, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoDeCompra(requestId, principal);
        Map<UUID, BigDecimal> pendientes = autorizados(requestId, null);
        Map<UUID, BigDecimal> totales = new HashMap<>();
        jdbc.query("""
                select n.request_item_id, sum(n.qty) from necesidad_compra n
                join requerimiento_item ri on ri.request_item_id = n.request_item_id
                where ri.request_id = ? and n.status = 'CAJA_CHICA' group by n.request_item_id""", rs -> {
            totales.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
        }, requestId);
        List<FormularioCajaChicaDto.Item> items = new ArrayList<>();
        boolean hayPendiente = false;
        for (RequerimientoItem i : requerimientoItemRepository.findByRequerimiento(requestId)) {
            BigDecimal total = totales.get(i.getRequestItemId());
            if (total != null) {
                BigDecimal pendiente = pendientes.getOrDefault(i.getRequestItemId(), BigDecimal.ZERO);
                hayPendiente |= pendiente.signum() > 0;
                items.add(new FormularioCajaChicaDto.Item(i.getRequestItemId(),
                        i.getVariante() != null ? i.getVariante().getInternalVariantCode() : i.getProducto().getSkuBase(),
                        i.getProducto().getName(), i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                        i.getUom().getCode(), total, pendiente));
            }
        }
        boolean enAtencion = EstadoRequerimiento.EN_ATENCION.equals(r.getStatus())
                || EstadoRequerimiento.ATENCION_PARCIAL.equals(r.getStatus());
        String ceco = r.getCeco() != null ? r.getCeco().getCode() + " — " + r.getCeco().getName() : null;
        return new FormularioCajaChicaDto(r.getRequestId(), r.getRequestCode(), r.getEmpresa().getCode(), ceco,
                r.getStatus(), enAtencion && hayPendiente, items,
                conItems(jdbc.query(SELECT + " where r.request_id = ? order by r.created_at", (rs, i) -> mapear(rs, List.of()), requestId)));
    }

    /** Datos del comprobante ya validados y la clave de duplicado. */
    private record Comprobante(String tipo, String moneda, String ruc, String nombre, String serie, String numero,
                               BigDecimal total, String clave, UUID supplierId) {
    }

    private Comprobante comprobante(RegistrarCajaChicaRequest req, Map<UUID, RequerimientoItem> itemsReq,
                                    Map<UUID, BigDecimal> autorizados) {
        String tipo = req.tipoComprobante().trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_COMPROBANTE.contains(tipo)) {
            throw ApiException.badRequest("Tipo de comprobante invalido.");
        }
        String moneda = req.moneda().trim().toUpperCase(Locale.ROOT);
        Integer monedaExiste = jdbc.queryForObject("select count(*) from moneda where currency_code = ?", Integer.class, moneda);
        if (monedaExiste == null || monedaExiste == 0) {
            throw ApiException.badRequest("Moneda invalida.");
        }
        if (req.fechaCompra().isAfter(LocalDate.now(LIMA))) {
            throw ApiException.badRequest("La fecha de compra no puede ser futura.");
        }
        Set<UUID> vistos = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RegistrarCajaChicaRequest.Item in : req.items()) {
            RequerimientoItem item = itemsReq.get(in.requestItemId());
            if (item == null) {
                throw ApiException.badRequest("Uno de los items no pertenece al requerimiento.");
            }
            if (!vistos.add(in.requestItemId())) {
                throw ApiException.badRequest("Un item esta repetido.");
            }
            BigDecimal autorizado = autorizados.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO);
            if (autorizado.signum() <= 0) {
                throw ApiException.badRequest("Logistica no autorizo comprar " + item.getProducto().getSkuBase()
                        + " con caja chica.");
            }
            if (in.qty().compareTo(autorizado) > 0) {
                throw ApiException.badRequest("La cantidad de " + item.getProducto().getSkuBase()
                        + " supera lo autorizado por caja chica (" + autorizado.stripTrailingZeros().toPlainString() + ").");
            }
            total = total.add(in.qty().multiply(in.costoUnitario()));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        if (total.signum() <= 0) {
            throw ApiException.badRequest("El monto de la compra debe ser mayor a 0.");
        }
        String ruc = blankToNull(req.proveedorRuc());
        String serie = req.serie() != null && !req.serie().isBlank() ? req.serie().trim().toUpperCase(Locale.ROOT) : null;
        String numero = req.numero().trim();
        String nombre = req.proveedorNombre().trim();
        String clave = String.join("|", ruc != null ? ruc : nombre.toUpperCase(Locale.ROOT),
                tipo, serie != null ? serie : "", numero.replaceFirst("^0+(?=.)", ""), total.toPlainString());
        UUID supplierId = ruc == null ? null : jdbc.queryForList("select supplier_id from proveedor where ruc = ?", UUID.class, ruc)
                .stream().findFirst().orElse(null);
        return new Comprobante(tipo, moneda, ruc, nombre, serie, numero, total, clave, supplierId);
    }

    private void exigirComprobanteUnico(String clave, UUID excluirRef) {
        List<String> duplicado = jdbc.queryForList("""
                select petty_cash_code from caja_chica_ref where duplicate_control_key = ?
                  and petty_cash_ref_id is distinct from cast(? as uuid)""", String.class, clave, excluirRef);
        if (!duplicado.isEmpty()) {
            throw ApiException.badRequest("Comprobante duplicado: ya se registro en " + duplicado.get(0) + ".");
        }
    }

    private void exigirDocumento(UUID documentId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from documento where document_id = ? and document_type = 'COMPROBANTE_CAJA_CHICA'",
                Integer.class, documentId);
        if (n == null || n == 0) {
            throw ApiException.badRequest("Adjunta la foto o el PDF del comprobante.");
        }
    }

    private void insertarItems(UUID refId, RegistrarCajaChicaRequest req, Map<UUID, RequerimientoItem> itemsReq) {
        List<UUID> idsItems = req.items().stream().map(RegistrarCajaChicaRequest.Item::requestItemId).distinct().toList();
        Map<UUID, UUID> necesidadPorItem = new HashMap<>();
        jdbc.query("""
                select distinct on (request_item_id) request_item_id, need_id from necesidad_compra
                where request_item_id in (%s) and status = 'CAJA_CHICA'
                order by request_item_id, created_at""".formatted(String.join(",", Collections.nCopies(idsItems.size(), "?"))),
                rs -> {
                    necesidadPorItem.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
                }, idsItems.toArray());
        for (RegistrarCajaChicaRequest.Item in : req.items()) {
            RequerimientoItem item = itemsReq.get(in.requestItemId());
            jdbc.update("""
                    insert into caja_chica_item (petty_cash_item_id, petty_cash_ref_id, request_item_id, product_id,
                      variant_id, quantity, unit_cost, line_total, need_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    UUID.randomUUID(), refId, item.getRequestItemId(), item.getProducto().getProductId(),
                    item.getVariante() != null ? item.getVariante().getVariantId() : null, in.qty(), in.costoUnitario(),
                    in.qty().multiply(in.costoUnitario()).setScale(2, RoundingMode.HALF_UP),
                    necesidadPorItem.get(item.getRequestItemId()));
        }
    }

    private Map<UUID, RequerimientoItem> itemsDe(UUID requestId) {
        Map<UUID, RequerimientoItem> out = new HashMap<>();
        for (RequerimientoItem i : requerimientoItemRepository.findByRequerimiento(requestId)) {
            out.put(i.getRequestItemId(), i);
        }
        return out;
    }

    /**
     * El proyecto ya compro con su caja chica (RF-027/029/056, DEC-L054): lo
     * registra su solicitante (o Logistica en su nombre) con el comprobante. Lo
     * comprado se queda en el proyecto: no entra al almacen ni se reserva, y
     * cuenta como atendido cuando Logistica regulariza.
     */
    @Transactional
    public CajaChicaDto registrar(UUID requestId, RegistrarCajaChicaRequest req, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoDeCompra(requestId, principal);
        if (!EstadoRequerimiento.EN_ATENCION.equals(r.getStatus()) && !EstadoRequerimiento.ATENCION_PARCIAL.equals(r.getStatus())) {
            throw ApiException.badRequest("El requerimiento no esta en atencion (estado " + r.getStatus() + ").");
        }
        // Lock de la empresa: numeracion CC-<n> sin carreras.
        Empresa empresa = empresaRepository.bloquear(r.getEmpresa().getCompanyId()).orElseThrow();
        Map<UUID, RequerimientoItem> itemsReq = itemsDe(requestId);
        Comprobante c = comprobante(req, itemsReq, autorizados(requestId, null));
        exigirComprobanteUnico(c.clave(), null);
        if (req.documentId() == null) {
            throw ApiException.badRequest("Adjunta la foto o el PDF del comprobante.");
        }
        exigirDocumento(req.documentId());

        // Motivo: el de la ruta que autorizo Logistica (la sugerencia del solicitante o la regla).
        List<String> motivos = jdbc.queryForList("""
                select n.suggested_reason from necesidad_compra n join requerimiento_item ri on ri.request_item_id = n.request_item_id
                where ri.request_id = ? and n.status = 'CAJA_CHICA' and n.suggested_reason is not null
                order by n.created_at limit 1""", String.class, requestId);
        String motivo = motivos.isEmpty() ? EXCEPCION : motivos.get(0);
        String detalle = motivos.isEmpty() ? "Ruta autorizada por Logistica en Necesidades" : null;
        UUID reglaId = null;
        try {
            reglaId = regla(empresa.getCompanyId()).id();
        } catch (ApiException sinRegla) {
            // La regla solo sugiere: sin ella igual se registra lo autorizado.
        }

        Integer previas = jdbc.queryForObject("select count(*) from caja_chica_ref where company_id = ?", Integer.class,
                empresa.getCompanyId());
        String codigo = "CC-" + ((previas == null ? 0 : previas) + 1) + "-" + empresa.getCode();
        UUID refId = UUID.randomUUID();
        jdbc.update("""
                insert into caja_chica_ref (petty_cash_ref_id, petty_cash_code, company_id, request_id,
                  purchase_date, supplier_id, supplier_ruc, supplier_name, amount, currency_code, document_type,
                  document_series, document_number, document_id, duplicate_control_key, route_reason_code,
                  route_reason_detail, route_rule_id, purchase_user_id, destination)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PROYECTO')""",
                refId, codigo, empresa.getCompanyId(), requestId, req.fechaCompra(),
                c.supplierId(), c.ruc(), c.nombre(), c.total(), c.moneda(), c.tipo(), c.serie(), c.numero(),
                req.documentId(), c.clave(), motivo, detalle, reglaId, principal.userId());
        insertarItems(refId, req, itemsReq);
        auditoria.registrar("CAJA_CHICA", refId, "REGISTRAR", Map.of(),
                Map.of("codigo", codigo, "monto", c.total().toPlainString(), "requerimiento", r.getRequestCode()),
                principal.userId());
        return obtener(refId);
    }

    /** El solicitante corrige una compra observada: vuelve a "por regularizar". */
    @Transactional
    public CajaChicaDto corregir(UUID id, RegistrarCajaChicaRequest req, UsuarioPrincipal principal) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                select request_id, regularization_status, destination from caja_chica_ref
                where petty_cash_ref_id = ? for update""", id);
        if (filas.isEmpty()) {
            throw ApiException.notFound("Compra por Caja Chica no encontrada.");
        }
        Map<String, Object> ref = filas.get(0);
        UUID requestId = (UUID) ref.get("request_id");
        requerimientoDeCompra(requestId, principal);
        if (!"OBSERVADO".equals(ref.get("regularization_status"))) {
            throw ApiException.badRequest("Solo se corrige una compra observada.");
        }
        if (!"PROYECTO".equals(ref.get("destination"))) {
            throw ApiException.badRequest("Esta compra entro al almacen: no se puede corregir.");
        }
        Map<UUID, RequerimientoItem> itemsReq = itemsDe(requestId);
        Comprobante c = comprobante(req, itemsReq, autorizados(requestId, id));
        exigirComprobanteUnico(c.clave(), id);
        if (req.documentId() != null) {
            exigirDocumento(req.documentId());
        }
        jdbc.update("delete from caja_chica_item where petty_cash_ref_id = ?", id);
        jdbc.update("""
                update caja_chica_ref set purchase_date = ?, supplier_id = ?, supplier_ruc = ?, supplier_name = ?,
                  amount = ?, currency_code = ?, document_type = ?, document_series = ?, document_number = ?,
                  document_id = coalesce(?, document_id), duplicate_control_key = ?,
                  regularization_status = 'PENDIENTE_REGULARIZACION'
                where petty_cash_ref_id = ?""",
                req.fechaCompra(), c.supplierId(), c.ruc(), c.nombre(), c.total(), c.moneda(), c.tipo(), c.serie(),
                c.numero(), req.documentId(), c.clave(), id);
        insertarItems(id, req, itemsReq);
        auditoria.registrar("CAJA_CHICA", id, "CORREGIR", Map.of("estado", "OBSERVADO"),
                Map.of("estado", "PENDIENTE_REGULARIZACION", "monto", c.total().toPlainString()), principal.userId());
        return obtener(id);
    }

    // ---------------------------------------------------------------- consulta y regularizacion

    public List<CajaChicaDto> listar(UsuarioPrincipal principal) {
        exigirGestor(principal);
        List<CajaChicaDto> refs = jdbc.query(SELECT
                + " order by (r.regularization_status in ('PENDIENTE_REGULARIZACION','OBSERVADO')) desc, r.created_at desc"
                + " limit 300", (rs, i) -> mapear(rs, List.of()));
        return conItems(refs);
    }

    public List<CajaChicaDto> listarPorRequerimiento(UUID requestId, UsuarioPrincipal principal) {
        requerimientoDeCompra(requestId, principal);
        return conItems(jdbc.query(SELECT + " where r.request_id = ? order by r.created_at", (rs, i) -> mapear(rs, List.of()), requestId));
    }

    /**
     * Logistica verifico el comprobante. Lo comprado para el proyecto recien
     * cuenta como atendido aqui (DEC-L054).
     */
    @Transactional
    public CajaChicaDto regularizar(UUID id, String codigoExterno, UsuarioPrincipal principal) {
        exigirGestor(principal);
        String estado = estado(id);
        if (!"PENDIENTE_REGULARIZACION".equals(estado) && !"OBSERVADO".equals(estado)) {
            throw ApiException.badRequest("Esta compra ya fue regularizada.");
        }
        jdbc.update("""
                update caja_chica_ref set regularization_status = 'REGULARIZADO', observed_reason = null,
                  external_movement_id = coalesce(?, external_movement_id), regularized_by = ?, regularized_at = now()
                where petty_cash_ref_id = ?""", blankToNull(codigoExterno), principal.userId(), id);
        auditoria.registrar("CAJA_CHICA", id, "REGULARIZAR", Map.of("estado", estado),
                Map.of("estado", "REGULARIZADO"), principal.userId());
        CajaChicaDto dto = obtener(id);
        Requerimiento r = requerimiento(dto.requestId());
        String nuevo = atencionCalculator.calcular(r);
        if (!nuevo.equals(r.getStatus())) {
            r.setStatus(nuevo);
            requerimientoRepository.save(r);
        }
        avisar(r, "REGULARIZADA", null);
        return dto;
    }

    @Transactional
    public CajaChicaDto observar(UUID id, String motivo, UsuarioPrincipal principal) {
        exigirGestor(principal);
        if (!"PENDIENTE_REGULARIZACION".equals(estado(id))) {
            throw ApiException.badRequest("Solo se observa una compra pendiente de regularizar.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Indica el motivo de la observacion.");
        }
        jdbc.update("update caja_chica_ref set regularization_status = 'OBSERVADO', observed_reason = ? where petty_cash_ref_id = ?",
                motivo.trim(), id);
        auditoria.registrar("CAJA_CHICA", id, "OBSERVAR", Map.of("estado", "PENDIENTE_REGULARIZACION"),
                Map.of("estado", "OBSERVADO", "motivo", motivo.trim()), principal.userId());
        CajaChicaDto dto = obtener(id);
        avisar(requerimiento(dto.requestId()), "OBSERVADA", motivo.trim());
        return dto;
    }

    /** Aviso en vivo al solicitante (DEC-L054): autorizada, observada o regularizada. */
    public void avisar(Requerimiento r, String evento, String detalle) {
        if (r.getRequester() != null) {
            realtimeNotifier.cajaChica(r.getRequester().getUserId(),
                    new RealtimeNotifier.AvisoCajaChica(r.getRequestId(), r.getRequestCode(), evento, detalle));
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static final String SELECT = """
            select r.petty_cash_ref_id, r.petty_cash_code, r.request_id, q.request_code, e.code, r.purchase_date,
              r.supplier_name, r.supplier_ruc, r.document_type, r.document_series, r.document_number, r.currency_code,
              r.amount, r.route_reason_code, r.route_reason_detail, r.regularization_status, r.observed_reason,
              r.external_movement_id, d.file_url, u.display_name, r.created_at, r.destination
            from caja_chica_ref r
            join requerimiento q on q.request_id = r.request_id
            join empresa e on e.company_id = r.company_id
            join usuario u on u.user_id = r.purchase_user_id
            left join documento d on d.document_id = r.document_id""";

    private static CajaChicaDto mapear(ResultSet rs, List<CajaChicaDto.Item> items) throws SQLException {
        String serie = rs.getString(10);
        String comprobante = rs.getString(9) + " " + (serie != null ? serie + "-" : "") + rs.getString(11);
        Timestamp creado = rs.getTimestamp(21);
        return new CajaChicaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                rs.getString(5), rs.getObject(6, LocalDate.class), rs.getString(7), rs.getString(8), comprobante,
                rs.getString(12), rs.getBigDecimal(13), rs.getString(14), rs.getString(15), rs.getString(16),
                rs.getString(17), rs.getString(18), rs.getString(19), rs.getString(20),
                creado != null ? creado.toInstant() : null, rs.getString(22), items);
    }

    /** Items de varias compras en una sola consulta. */
    private List<CajaChicaDto> conItems(List<CajaChicaDto> refs) {
        if (refs.isEmpty()) {
            return refs;
        }
        Map<UUID, List<CajaChicaDto.Item>> items = new HashMap<>();
        String marcas = String.join(",", Collections.nCopies(refs.size(), "?"));
        jdbc.query("""
                select ci.petty_cash_ref_id, coalesce(v.internal_variant_code, p.sku_base), p.name, ci.quantity,
                  ci.unit_cost, ci.line_total, ci.request_item_id
                from caja_chica_item ci join producto p on p.product_id = ci.product_id
                left join producto_variante v on v.variant_id = ci.variant_id
                where ci.petty_cash_ref_id in (""" + marcas + ") order by 2", rs -> {
            items.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(new CajaChicaDto.Item(
                    rs.getObject(7, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                    rs.getBigDecimal(6)));
        }, refs.stream().map(CajaChicaDto::id).toArray());
        return refs.stream().map(c -> new CajaChicaDto(c.id(), c.codigo(), c.requestId(), c.requestCode(), c.empresaCode(),
                c.fechaCompra(), c.proveedor(), c.proveedorRuc(), c.comprobante(), c.moneda(), c.monto(), c.motivo(),
                c.motivoDetalle(), c.estado(), c.observacion(), c.codigoExterno(), c.documentoUrl(), c.registradoPor(),
                c.registradoEn(), c.destino(), items.getOrDefault(c.id(), List.of()))).toList();
    }

    private CajaChicaDto obtener(UUID id) {
        List<CajaChicaDto> r = jdbc.query(SELECT + " where r.petty_cash_ref_id = ?", (rs, i) -> mapear(rs, List.of()), id);
        if (r.isEmpty()) {
            throw ApiException.notFound("Compra por Caja Chica no encontrada.");
        }
        return conItems(r).get(0);
    }

    private String estado(UUID id) {
        List<String> e = jdbc.queryForList(
                "select regularization_status from caja_chica_ref where petty_cash_ref_id = ? for update", String.class, id);
        if (e.isEmpty()) {
            throw ApiException.notFound("Compra por Caja Chica no encontrada.");
        }
        return e.get(0);
    }

    private Requerimiento requerimiento(UUID id) {
        return requerimientoRepository.findByIdsConDetalle(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
