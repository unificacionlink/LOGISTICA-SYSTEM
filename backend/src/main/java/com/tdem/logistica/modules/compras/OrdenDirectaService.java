package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoOrden;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.AppConstants.TipoOrden;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Moneda;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.entity.OrdenItem;
import com.tdem.logistica.entity.ProductoVariante;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.compras.dto.CrearOrdenDirectaRequest;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.MonedaRepository;
import com.tdem.logistica.repository.OrdenAbastecimientoRepository;
import com.tdem.logistica.repository.OrdenItemRepository;
import com.tdem.logistica.repository.ProductoVarianteRepository;
import com.tdem.logistica.repository.ProveedorRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OC directa (DEC-L080): Logistica compra sin solicitud de cotizacion, con
 * productos del catalogo y/o servicios en texto libre. Sale aprobada (la emite
 * quien decide compras) y con las mismas condiciones del formato F-LOG-09.
 * Los productos siguen el camino normal: liberar y recibir en el almacen
 * central. Una orden solo de servicios no pasa por el almacen y se cierra al
 * registrar su pago.
 */
@Service
@Transactional(readOnly = true)
public class OrdenDirectaService {

    /** Fieles a las opciones de la cotizacion (components/compras/condiciones.ts). */
    static final Set<String> FORMAS_PAGO = Set.of("CONTADO", "ANTICIPO", "CREDITO_30");
    static final Set<String> ENTREGAS = Set.of("AGENCIA", "ALMACEN_PROVEEDOR", "ALMACEN_CLIENTE");

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final OrdenAbastecimientoRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final EmpresaRepository empresaRepository;
    private final ProveedorRepository proveedorRepository;
    private final MonedaRepository monedaRepository;
    private final CecoRepository cecoRepository;
    private final ProductoVarianteRepository varianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate jdbc;
    private final OrigenCompras origen;
    private final Auditoria auditoria;
    private final RealtimeNotifier realtime;

    public OrdenDirectaService(OrdenAbastecimientoRepository ordenRepository, OrdenItemRepository ordenItemRepository,
                               EmpresaRepository empresaRepository, ProveedorRepository proveedorRepository,
                               MonedaRepository monedaRepository, CecoRepository cecoRepository,
                               ProductoVarianteRepository varianteRepository, UsuarioRepository usuarioRepository,
                               JdbcTemplate jdbc, OrigenCompras origen, Auditoria auditoria,
                               RealtimeNotifier realtime) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.empresaRepository = empresaRepository;
        this.proveedorRepository = proveedorRepository;
        this.monedaRepository = monedaRepository;
        this.cecoRepository = cecoRepository;
        this.varianteRepository = varianteRepository;
        this.usuarioRepository = usuarioRepository;
        this.jdbc = jdbc;
        this.origen = origen;
        this.auditoria = auditoria;
        this.realtime = realtime;
    }

    @Transactional
    public OrdenDto crear(CrearOrdenDirectaRequest r, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para emitir ordenes de compra.");
        }
        Empresa empresa = empresaRepository.findByCode(r.empresaCode().trim())
                .filter(Empresa::isActive)
                .orElseThrow(() -> ApiException.badRequest("Empresa no encontrada."));
        Proveedor proveedor = proveedorRepository.findById(r.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Proveedor no encontrado."));
        if (!proveedor.isActive()) {
            throw ApiException.badRequest("El proveedor no esta habilitado para compras.");
        }
        Moneda moneda = monedaRepository.findById(r.currencyCode().trim().toUpperCase())
                .filter(Moneda::isActive)
                .orElseThrow(() -> ApiException.badRequest("Moneda no valida."));
        String formaPago = opcion(r.paymentCondition(), FORMAS_PAGO, "Forma de pago no valida.");
        String entrega = opcion(r.deliveryMode(), ENTREGAS, "Modalidad de entrega no valida.");
        String modoIgv = ImpuestoOrden.validarModo(r.taxMode());
        Ceco ceco = ceco(r.cecoId(), empresa);
        UUID requestId = requerimiento(r.requestCode(), empresa);
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));

        OrdenAbastecimiento orden = new OrdenAbastecimiento();
        orden.setOrderId(UUID.randomUUID());
        orden.setOrderCode(CodigoCompra.generar("OC", empresa.getCode()));
        orden.setEmpresa(empresa);
        orden.setProveedor(proveedor);
        orden.setOrderType(TipoOrden.OC_DIRECTA);
        orden.setIssueDate(LocalDate.now(LIMA));
        orden.setPaymentCondition(formaPago);
        orden.setDeliveryMode(entrega);
        orden.setDeliveryLocation(limpio(r.deliveryLocation()));
        orden.setDeliveryTerm(limpio(r.deliveryTerm()));
        orden.setAdditionalSpecs(limpio(r.additionalSpecs()));
        orden.setMoneda(moneda);
        orden.setTaxMode(modoIgv);
        orden.setCeco(ceco);
        orden.setRequestId(requestId);
        // La emite quien aprueba compras: sale aprobada, sin pasar por Gerencia (decision del usuario).
        orden.setStatus(EstadoOrden.EMITIDA);
        Instant ahora = Instant.now();
        orden.setApprovedAt(ahora);
        orden.setApprovedBy(principal.userId());
        orden.setCreatedBy(usuario);

        List<OrdenItem> items = lineas(r.lineas(), orden);
        orden.setServicesOnly(items.stream().allMatch(i -> i.getProducto() == null));
        BigDecimal suma = items.stream().map(OrdenItem::getLineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        ImpuestoOrden.aplicar(orden, suma);
        ordenRepository.insertar(orden);
        ordenItemRepository.insertarTodos(items);
        // Lo que sigue (proveedor_producto, OrigenCompras) va por JDBC: tiene que ver la orden ya escrita.
        ordenRepository.flush();

        // Como en la OC por cotizacion: el proveedor queda habilitado para esos productos.
        Set<UUID> productos = items.stream().map(OrdenItem::getProducto).filter(Objects::nonNull)
                .map(p -> p.getProductId()).collect(Collectors.toSet());
        for (UUID productId : productos) {
            jdbc.update("""
                    insert into proveedor_producto (product_id, supplier_id, created_by) values (?, ?, ?)
                    on conflict do nothing""", productId, proveedor.getSupplierId(), principal.userId());
        }

        Map<String, Object> despues = new HashMap<>();
        despues.put("orden", orden.getOrderCode());
        despues.put("proveedor", proveedor.getRuc());
        despues.put("lineas", items.size());
        despues.put("servicios", items.stream().filter(i -> i.getProducto() == null).count());
        despues.put("total", orden.getTotal());
        despues.put("ceco", ceco != null ? ceco.getCode() : null);
        despues.put("requerimiento", requestId);
        auditoria.registrar("ORDEN_ABASTECIMIENTO", orden.getOrderId(), "EMITIR_DIRECTA", null, despues,
                principal.userId());
        OrdenDto dto = ComprasMapper.toDto(orden, origen.deOrden(orden.getOrderId()));
        realtime.ordenDeCompra(dto);
        return dto;
    }

    private List<OrdenItem> lineas(List<CrearOrdenDirectaRequest.Linea> lineas, OrdenAbastecimiento orden) {
        List<UUID> ids = lineas.stream().map(CrearOrdenDirectaRequest.Linea::variantId).filter(Objects::nonNull).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.badRequest("Un producto esta repetido: junta sus cantidades en una sola linea.");
        }
        Map<UUID, ProductoVariante> variantes = ids.isEmpty() ? Map.of()
                : varianteRepository.findActivasPorId(ids).stream()
                .collect(Collectors.toMap(ProductoVariante::getVariantId, Function.identity()));
        List<OrdenItem> items = new ArrayList<>();
        for (CrearOrdenDirectaRequest.Linea l : lineas) {
            OrdenItem oi = new OrdenItem();
            oi.setOrderItemId(UUID.randomUUID());
            oi.setOrden(orden);
            if (l.variantId() != null) {
                ProductoVariante v = variantes.get(l.variantId());
                if (v == null) {
                    throw ApiException.badRequest("Uno de los productos no existe o esta inactivo.");
                }
                String atributo = v.getAttributeValue() != null && !v.getAttributeValue().isBlank()
                        ? " (" + v.getAttributeValue() + ")" : "";
                oi.setProducto(v.getProducto());
                oi.setVariante(v);
                oi.setDescription(recortar(v.getProducto().getName() + atributo));
                oi.setUom(v.getProducto().getBaseUom());
            } else {
                String descripcion = limpio(l.descripcion());
                if (descripcion == null) {
                    throw ApiException.badRequest("Describe el servicio de cada linea sin producto.");
                }
                oi.setDescription(descripcion);
            }
            BigDecimal subtotal = l.cantidad().multiply(l.precioUnitario()).setScale(2, RoundingMode.HALF_UP);
            oi.setQtyOrdered(l.cantidad());
            oi.setUnitCost(l.precioUnitario().setScale(4, RoundingMode.HALF_UP));
            oi.setLineSubtotal(subtotal);
            oi.setTaxAmount(BigDecimal.ZERO);
            oi.setLineTotal(subtotal);
            items.add(oi);
        }
        return items;
    }

    private Ceco ceco(UUID cecoId, Empresa empresa) {
        if (cecoId == null) {
            return null;
        }
        Ceco c = cecoRepository.findById(cecoId)
                .filter(Ceco::isActive)
                .orElseThrow(() -> ApiException.badRequest("CECO no encontrado."));
        if (!c.getEmpresa().getCompanyId().equals(empresa.getCompanyId())) {
            throw ApiException.badRequest("El CECO no es de la empresa de la orden.");
        }
        return c;
    }

    private UUID requerimiento(String requestCode, Empresa empresa) {
        String codigo = limpio(requestCode);
        if (codigo == null) {
            return null;
        }
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select request_id, company_id from requerimiento where upper(request_code) = upper(?)", codigo);
        if (filas.isEmpty()) {
            throw ApiException.badRequest("No existe el requerimiento " + codigo + ".");
        }
        if (!empresa.getCompanyId().equals(filas.get(0).get("company_id"))) {
            throw ApiException.badRequest("El requerimiento " + codigo + " no es de la empresa de la orden.");
        }
        return (UUID) filas.get(0).get("request_id");
    }

    private static String opcion(String valor, Set<String> validas, String error) {
        String v = valor == null ? "" : valor.trim().toUpperCase();
        if (!validas.contains(v)) {
            throw ApiException.badRequest(error);
        }
        return v;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s) {
        return s.length() <= 250 ? s : s.substring(0, 250);
    }
}
