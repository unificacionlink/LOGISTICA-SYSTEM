package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EntidadAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoOrden;
import com.tdem.logistica.common.AppConstants.EstadoPagoOrden;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Aprobacion;
import com.tdem.logistica.entity.Documento;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.entity.OrdenItem;
import com.tdem.logistica.modules.almacen.DestinosEntrega;
import com.tdem.logistica.modules.compras.dto.CondicionesOrdenRequest;
import com.tdem.logistica.modules.compras.dto.DecidirOrdenRequest;
import com.tdem.logistica.modules.compras.dto.LiberarOrdenRequest;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.modules.compras.dto.RegistrarPagoRequest;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.AprobacionRepository;
import com.tdem.logistica.repository.DocumentoRepository;
import com.tdem.logistica.repository.OrdenAbastecimientoRepository;
import com.tdem.logistica.repository.OrdenItemRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ciclo de la OC despues de emitirla (Lote 17, DEC-L061, V38.7 approveBuy /
 * cvCoordinate / caRelease): aprobacion, pago y liberacion para el almacen.
 * <ul>
 *   <li>Deciden GERENCIA y LOGISTICA (ADMIN de respaldo); nadie decide la OC que emitio.</li>
 *   <li>Todas las OC pasan por aprobacion, sin umbral por monto (PD-04).</li>
 *   <li>El pago es control: no frena la recepcion, solo impide anular.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class OrdenCompraService {

    private static final String ENTIDAD_AUDITORIA = "ORDEN_ABASTECIMIENTO";

    private final OrdenAbastecimientoRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final AprobacionRepository aprobacionRepository;
    private final DocumentoRepository documentoRepository;
    private final ComprasService comprasService;
    private final OrigenCompras origen;
    private final Auditoria auditoria;
    private final RealtimeNotifier realtime;
    private final DestinosEntrega destinosEntrega;
    private final PuntosEntregaService puntos;
    private final com.tdem.logistica.repository.DocumentoRelacionRepository documentoRelacionRepository;

    public OrdenCompraService(OrdenAbastecimientoRepository ordenRepository,
                              OrdenItemRepository ordenItemRepository,
                              AprobacionRepository aprobacionRepository,
                              DocumentoRepository documentoRepository,
                              ComprasService comprasService,
                              OrigenCompras origen,
                              Auditoria auditoria,
                              RealtimeNotifier realtime,
                              DestinosEntrega destinosEntrega,
                              PuntosEntregaService puntos,
                              com.tdem.logistica.repository.DocumentoRelacionRepository documentoRelacionRepository) {
        this.puntos = puntos;
        this.documentoRelacionRepository = documentoRelacionRepository;
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.aprobacionRepository = aprobacionRepository;
        this.documentoRepository = documentoRepository;
        this.comprasService = comprasService;
        this.origen = origen;
        this.auditoria = auditoria;
        this.realtime = realtime;
        this.destinosEntrega = destinosEntrega;
    }

    /** Quien puede aprobar o rechazar una OC. */
    public static boolean esDecisorDeOrden(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.GERENCIA, Roles.LOGISTICA, Roles.ADMIN);
    }

    private static void exigirDecisor(UsuarioPrincipal p) {
        if (!esDecisorDeOrden(p)) {
            throw ApiException.forbidden("No tienes permiso para aprobar ordenes de compra.");
        }
    }

    private static void exigirLogisticaOAdmin(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar ordenes de compra.");
        }
    }

    /** Al emitir (AbastecimientoService.emitirOrden): deja la aprobacion pendiente y avisa. */
    @Transactional
    void abrirAprobacion(OrdenAbastecimiento orden, OrdenDto dto) {
        Aprobacion a = new Aprobacion();
        a.setApprovalId(UUID.randomUUID());
        a.setEntityType(EntidadAprobacion.COMPRA);
        a.setEntityId(orden.getOrderId());
        a.setStatus(EstadoAprobacion.PENDIENTE);
        aprobacionRepository.insertar(a);
        realtime.ordenDeCompra(dto);
    }

    /** OC esperando decision que este usuario puede tomar (no las que emitio). */
    public List<OrdenDto> listarPorAprobar(UsuarioPrincipal principal) {
        exigirDecisor(principal);
        List<OrdenAbastecimiento> ordenes = ordenRepository.findByStatusInConDetalle(List.of(EstadoOrden.POR_APROBAR))
                .stream()
                .filter(o -> !o.getCreatedBy().getUserId().equals(principal.userId()))
                .toList();
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origen.deOrdenes(
                ordenes.stream().map(OrdenAbastecimiento::getOrderId).toList());
        return ordenes.stream()
                .map(o -> ComprasMapper.toDto(o, refs.getOrDefault(o.getOrderId(), List.of())))
                .toList();
    }

    @Transactional
    public OrdenDto decidir(UUID orderId, DecidirOrdenRequest request, UsuarioPrincipal principal) {
        exigirDecisor(principal);
        String decision = request.decision().trim().toUpperCase();
        boolean aprueba = EstadoAprobacion.APROBADO.equals(decision);
        if (!aprueba && !EstadoAprobacion.RECHAZADO.equals(decision)) {
            throw ApiException.badRequest("Decision invalida: usa APROBADO o RECHAZADO.");
        }
        String motivo = aprueba ? null : ComprasService.exigirMotivo(request.motivo(), "rechazar la orden");

        OrdenAbastecimiento orden = bloquearConDetalle(orderId);
        if (!EstadoOrden.POR_APROBAR.equals(orden.getStatus())) {
            throw ApiException.badRequest("Esta orden ya no esta pendiente de aprobacion.");
        }
        if (orden.getCreatedBy().getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("No puedes aprobar una orden de compra que emitiste.");
        }

        Aprobacion a = aprobacionRepository.findPendientePorEntidad(EntidadAprobacion.COMPRA, orderId)
                .orElseGet(() -> {
                    Aprobacion nueva = new Aprobacion();
                    nueva.setApprovalId(UUID.randomUUID());
                    nueva.setEntityType(EntidadAprobacion.COMPRA);
                    nueva.setEntityId(orderId);
                    return nueva;
                });
        a.setStatus(decision);
        a.setDecidedBy(principal.userId());
        a.setDecidedAt(OffsetDateTime.now());
        a.setDecisionReason(motivo);
        aprobacionRepository.save(a);

        if (aprueba) {
            orden.setStatus(EstadoOrden.EMITIDA);
            orden.setApprovedAt(Instant.now());
            orden.setApprovedBy(principal.userId());
            ordenRepository.save(orden);
            auditoria.registrar(ENTIDAD_AUDITORIA, orderId, "APROBAR", Map.of("estado", EstadoOrden.POR_APROBAR),
                    Map.of("estado", EstadoOrden.EMITIDA), principal.userId());
        } else {
            // Rechazar = anular: los consolidados vuelven a cotizacion para elegir otra oferta.
            comprasService.anularInterna(orden, motivo, "RECHAZAR", principal.userId());
        }
        OrdenDto dto = ComprasMapper.toDto(orden, origen.deOrden(orderId));
        realtime.ordenDeCompra(dto);
        return dto;
    }

    /**
     * Condiciones del formato F-LOG-09 (DEC-L079): IGV, plazo y lugar de entrega y
     * especificaciones adicionales. Solo mientras espera aprobacion: lo que Gerencia
     * aprueba es el documento que recibe el proveedor.
     */
    @Transactional
    public OrdenDto editarCondiciones(UUID orderId, CondicionesOrdenRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        String modo = ImpuestoOrden.validarModo(request.taxMode());
        OrdenAbastecimiento orden = bloquearConDetalle(orderId);
        if (!EstadoOrden.POR_APROBAR.equals(orden.getStatus())) {
            throw ApiException.badRequest("Solo se editan las condiciones de una orden pendiente de aprobacion.");
        }
        Map<String, Object> antes = condiciones(orden);
        orden.setTaxMode(modo);
        PlazoEntrega.aplicar(orden, request.deliveryDays(), request.deliveryDayType());
        puntos.aplicar(orden, request.deliveryPointId());
        orden.setAdditionalSpecs(limpio(request.additionalSpecs()));
        List<OrdenItem> items = ordenItemRepository.findByOrden(orderId);
        if (request.entregaDirecta() != null) {
            if (request.entregaDirecta()) {
                // Cada unidad tiene que tener a que CECO ir (lanza si no, DEC-L084).
                destinosEntrega.destinos(orden, items);
            }
            orden.setDirectDelivery(request.entregaDirecta());
        }
        BigDecimal suma = items.stream()
                .map(i -> i.getLineSubtotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        ImpuestoOrden.aplicar(orden, suma);
        ordenRepository.save(orden);
        auditoria.registrar(ENTIDAD_AUDITORIA, orderId, "CONDICIONES", antes, condiciones(orden), principal.userId());
        return ComprasMapper.toDto(orden, origen.deOrden(orderId));
    }

    private static Map<String, Object> condiciones(OrdenAbastecimiento o) {
        Map<String, Object> m = new HashMap<>();
        m.put("igv", o.getTaxMode());
        m.put("total", o.getTotal());
        m.put("plazo", o.getDeliveryTerm());
        m.put("lugar", o.getDeliveryLocation());
        m.put("especificaciones", o.getAdditionalSpecs());
        m.put("entregaDirecta", o.isDirectDelivery());
        return m;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Anticipo o pago con su constancia. No se vuelve a PENDIENTE: lo entregado no se deshace. */
    @Transactional
    public OrdenDto registrarPago(UUID orderId, RegistrarPagoRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        String estado = request.estado().trim().toUpperCase();
        if (!EstadoPagoOrden.ANTICIPO.equals(estado) && !EstadoPagoOrden.PAGADO.equals(estado)) {
            throw ApiException.badRequest("Estado de pago invalido: usa ANTICIPO o PAGADO.");
        }
        if (request.documentId() == null) {
            throw ApiException.badRequest("Adjunta la constancia del pago.");
        }
        OrdenAbastecimiento orden = bloquearConDetalle(orderId);
        if (EstadoOrden.POR_APROBAR.equals(orden.getStatus())) {
            throw ApiException.badRequest("Aprueba la orden antes de registrar su pago.");
        }
        if (EstadoOrden.ANULADA.equals(orden.getStatus())) {
            throw ApiException.badRequest("La orden esta anulada.");
        }
        if (EstadoPagoOrden.PAGADO.equals(orden.getPaymentStatus())) {
            throw ApiException.badRequest("Esta orden ya esta pagada.");
        }
        Documento constancia = documentoRepository.findById(request.documentId())
                .filter(d -> "CONSTANCIA_PAGO".equals(d.getDocumentType()))
                .orElseThrow(() -> ApiException.badRequest("Constancia de pago no encontrada."));
        // DEC-L089: el pago final exige la factura del proveedor; en el anticipo es opcional.
        String facturaNumero = request.facturaNumero() == null ? null : request.facturaNumero().trim().toUpperCase();
        if (facturaNumero != null && facturaNumero.isEmpty()) {
            facturaNumero = null;
        }
        boolean pagoFinal = EstadoPagoOrden.PAGADO.equals(estado);
        if (pagoFinal && (facturaNumero == null || request.facturaDocumentId() == null)
                && orden.getFactura() == null) {
            throw ApiException.badRequest("Para el pago final adjunta la factura del proveedor y su número.");
        }
        if ((facturaNumero == null) != (request.facturaDocumentId() == null)) {
            throw ApiException.badRequest("Indica el número de la factura y adjunta su archivo.");
        }
        if (facturaNumero != null && !facturaNumero.matches("[A-Z0-9]{1,6}-[0-9]{1,12}")) {
            throw ApiException.badRequest("El número de factura va como serie-número, por ejemplo F001-123.");
        }
        Documento factura = request.facturaDocumentId() == null ? null
                : documentoRepository.findById(request.facturaDocumentId())
                        .filter(d -> "FACTURA_PDF".equals(d.getDocumentType()))
                        .orElseThrow(() -> ApiException.badRequest("Factura no encontrada."));

        Map<String, Object> antes = new HashMap<>();
        antes.put("pago", orden.getPaymentStatus());
        orden.setPaymentStatus(estado);
        orden.setConstanciaPago(constancia);
        orden.setPaidAt(Instant.now());
        orden.setPaidBy(principal.userId());
        relacionar(constancia, orderId);
        if (factura != null) {
            orden.setFactura(factura);
            orden.setInvoiceNumber(facturaNumero);
            relacionar(factura, orderId);
        }
        Map<String, Object> despues = new HashMap<>();
        despues.put("pago", estado);
        despues.put("constancia", constancia.getDocumentId());
        despues.put("factura", orden.getInvoiceNumber());
        // DEC-L080: la orden solo de servicios no tiene recepcion; pagada, queda cerrada.
        if (orden.isServicesOnly() && EstadoPagoOrden.PAGADO.equals(estado)) {
            antes.put("estado", orden.getStatus());
            orden.setStatus(EstadoOrden.CERRADA);
            despues.put("estado", EstadoOrden.CERRADA);
        }
        ordenRepository.save(orden);
        auditoria.registrar(ENTIDAD_AUDITORIA, orderId, "PAGO", antes, despues, principal.userId());
        return ComprasMapper.toDto(orden, origen.deOrden(orderId));
    }

    /** DEC-L089: cada constancia y factura queda ligada a la OC (el anticipo no se pierde al pagar el saldo). */
    private void relacionar(Documento documento, UUID orderId) {
        com.tdem.logistica.entity.DocumentoRelacion rel = new com.tdem.logistica.entity.DocumentoRelacion();
        rel.setDocumentRelationId(UUID.randomUUID());
        rel.setDocumento(documento);
        rel.setEntityType("ORDEN");
        rel.setEntityId(orderId);
        documentoRelacionRepository.insertar(rel);
    }

    /** Deja la OC aprobada lista para que el almacen la reciba; el pago no se exige. */
    @Transactional
    public OrdenDto liberar(UUID orderId, LiberarOrdenRequest request, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        OrdenAbastecimiento orden = bloquearConDetalle(orderId);
        if (EstadoOrden.POR_APROBAR.equals(orden.getStatus())) {
            throw ApiException.badRequest("Aprueba la orden antes de liberarla.");
        }
        if (!EstadoOrden.EMITIDA.equals(orden.getStatus())) {
            throw ApiException.badRequest("Esta orden no se puede liberar (estado " + orden.getStatus() + ").");
        }
        if (orden.isServicesOnly()) {
            throw ApiException.badRequest("Es una orden de servicios: no pasa por el almacén.");
        }
        if (orden.getReleasedAt() != null) {
            throw ApiException.badRequest("Esta orden ya esta liberada.");
        }
        String nota = request != null && request.nota() != null && !request.nota().isBlank()
                ? request.nota().trim() : null;
        orden.setReleasedAt(Instant.now());
        orden.setReleasedBy(principal.userId());
        orden.setReleaseNote(nota);
        ordenRepository.save(orden);
        Map<String, Object> despues = new HashMap<>();
        despues.put("liberada", true);
        despues.put("nota", nota);
        auditoria.registrar(ENTIDAD_AUDITORIA, orderId, "LIBERAR", null, despues, principal.userId());
        return ComprasMapper.toDto(orden, origen.deOrden(orderId));
    }

    /** Lock de la fila (decisiones concurrentes) y luego el detalle para el DTO. */
    private OrdenAbastecimiento bloquearConDetalle(UUID orderId) {
        ordenRepository.bloquear(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        return ordenRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
    }
}
