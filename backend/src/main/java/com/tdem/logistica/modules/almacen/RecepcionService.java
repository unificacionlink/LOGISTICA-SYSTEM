package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.compras.ComprasMapper;
import com.tdem.logistica.modules.compras.OrigenCompras;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recepcion de OC en almacen (RF-030/032/033, DEC-L017). Solo entra a stock lo
 * aceptado; lo danado/faltante se registra como incidencia y sigue pendiente
 * en la OC para una proxima recepcion. Lo recibido queda reservado para el
 * item de requerimiento que origino la compra (reserva RECEPCION_OC).
 */
@Service
@Transactional(readOnly = true)
public class RecepcionService {

    static final List<String> ESTADOS_POR_RECIBIR = List.of("EMITIDA", "EN_ATENCION", "RECEPCION_PARCIAL");
    static final Set<String> CONDICIONES = Set.of("CONFORME", "OBSERVADO");
    private static final Set<String> TIPOS_INCIDENCIA = Set.of("FALTANTE", "DANO", "PRODUCTO_ERRADO", "OTRO");
    private static final Set<String> ACCIONES_PROVEEDOR = Set.of("REPONER", "CAMBIO", "NOTA_CREDITO");
    private static final Set<String> ESTADOS_INCIDENCIA = Set.of("ABIERTA", "EN_GESTION", "CERRADA");

    private final OrdenAbastecimientoRepository ordenRepository;
    private final OrdenItemRepository ordenItemRepository;
    private final RecepcionRepository recepcionRepository;
    private final RecepcionItemRepository recepcionItemRepository;
    private final IncidenciaRecepcionRepository incidenciaRepository;
    private final GuiaRemisionRepository guiaRepository;
    private final AlmacenRepository almacenRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final ReservaStockRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NecesidadCompraRepository necesidadRepository;
    private final OrigenCompras origen;

    public RecepcionService(OrdenAbastecimientoRepository ordenRepository,
                            OrdenItemRepository ordenItemRepository,
                            RecepcionRepository recepcionRepository,
                            RecepcionItemRepository recepcionItemRepository,
                            IncidenciaRecepcionRepository incidenciaRepository,
                            GuiaRemisionRepository guiaRepository,
                            AlmacenRepository almacenRepository,
                            MovimientoInventarioRepository movimientoRepository,
                            ReservaStockRepository reservaRepository,
                            UsuarioRepository usuarioRepository,
                            NecesidadCompraRepository necesidadRepository,
                            OrigenCompras origen) {
        this.ordenRepository = ordenRepository;
        this.ordenItemRepository = ordenItemRepository;
        this.recepcionRepository = recepcionRepository;
        this.recepcionItemRepository = recepcionItemRepository;
        this.incidenciaRepository = incidenciaRepository;
        this.guiaRepository = guiaRepository;
        this.almacenRepository = almacenRepository;
        this.movimientoRepository = movimientoRepository;
        this.reservaRepository = reservaRepository;
        this.usuarioRepository = usuarioRepository;
        this.necesidadRepository = necesidadRepository;
        this.origen = origen;
    }

    static void exigirLogisticaOAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para operar el almacen.");
        }
    }

    public List<OrdenDto> listarPorRecibir(UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        List<OrdenAbastecimiento> ordenes = ordenRepository.findByStatusInConDetalle(ESTADOS_POR_RECIBIR);
        Map<UUID, List<OrigenCompras.ReqRef>> refs = origen.deOrdenes(
                ordenes.stream().map(OrdenAbastecimiento::getOrderId).toList());
        return ordenes.stream()
                .map(o -> ComprasMapper.toDto(o, refs.getOrDefault(o.getOrderId(), List.of())))
                .toList();
    }

    public RecepcionOrdenDto obtenerRecepcionOrden(UUID orderId, UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        OrdenAbastecimiento orden = ordenRepository.findDetalleById(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));

        List<OrdenItem> itemsOc = ordenItemRepository.findByOrden(orderId);
        // Una consulta para lo recibido de todos los items (antes una por item).
        Map<UUID, BigDecimal> recibido = recepcionItemRepository.recibidoPorItem(
                itemsOc.stream().map(OrdenItem::getOrderItemId).toList());
        List<ItemPorRecibirDto> items = itemsOc.stream()
                .map(i -> AlmacenMapper.toItemPorRecibir(i, recibido.getOrDefault(i.getOrderItemId(), BigDecimal.ZERO)))
                .toList();

        Map<UUID, List<RecepcionItem>> itemsPorRecepcion = recepcionItemRepository.findByOrden(orderId).stream()
                .collect(Collectors.groupingBy(i -> i.getRecepcion().getReceiptId()));
        Map<UUID, GuiaRemision> guiaPorRecepcion = guiaRepository.findByOrden(orderId).stream()
                .filter(g -> g.getRecepcion() != null)
                .collect(Collectors.toMap(g -> g.getRecepcion().getReceiptId(), Function.identity(), (a, b) -> a));
        List<RecepcionDto> recepciones = recepcionRepository.findByOrden(orderId).stream()
                .map(r -> AlmacenMapper.toDto(r,
                        itemsPorRecepcion.getOrDefault(r.getReceiptId(), List.of()),
                        guiaPorRecepcion.get(r.getReceiptId())))
                .toList();

        List<IncidenciaDto> incidencias = incidenciaRepository.findByOrden(orderId).stream()
                .map(AlmacenMapper::toDto)
                .toList();

        return new RecepcionOrdenDto(ComprasMapper.toDto(orden, origen.deOrden(orderId)), orden.getProveedor().getRuc(),
                items, recepciones, incidencias);
    }

    @Transactional
    public RecepcionOrdenDto registrar(UUID orderId, RegistrarRecepcionRequest request, UsuarioPrincipal principal) {
        registrarRecepcion(orderId, request, principal, "MANUAL");
        return obtenerRecepcionOrden(orderId, principal);
    }

    /** @param origen MANUAL (pantalla de recepcion) o CARGA_MASIVA (RF-031). */
    @Transactional
    void registrarRecepcion(UUID orderId, RegistrarRecepcionRequest request, UsuarioPrincipal principal, String origen) {
        exigirLogisticaOAdmin(principal);
        // Lock de la OC: numeracion de la recepcion y control del pendiente sin carreras.
        OrdenAbastecimiento orden = ordenRepository.bloquear(orderId)
                .orElseThrow(() -> ApiException.notFound("Orden de compra no encontrada."));
        if (!ESTADOS_POR_RECIBIR.contains(orden.getStatus())) {
            throw ApiException.badRequest("Esta orden no admite recepciones (estado " + orden.getStatus() + ").");
        }
        Usuario usuario = usuarioRepository.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        // DEC-L045: un solo almacen central (TDEM) para todas las empresas.
        Almacen almacen = almacenRepository.findCentralDelGrupo()
                .orElseThrow(() -> ApiException.badRequest("No hay un almacen central del grupo configurado."));

        Map<UUID, OrdenItem> itemsOrden = ordenItemRepository.findByOrden(orderId).stream()
                .collect(Collectors.toMap(OrdenItem::getOrderItemId, Function.identity()));
        Map<UUID, BigDecimal> recibidoPrevio = recepcionItemRepository.recibidoPorItem(itemsOrden.keySet());
        Set<UUID> vistos = new HashSet<>();
        boolean algoQueRegistrar = false;
        for (RecepcionItemInput in : request.items()) {
            OrdenItem oi = itemsOrden.get(in.orderItemId());
            if (oi == null) {
                throw ApiException.badRequest("Uno de los items no pertenece a esta orden.");
            }
            if (!vistos.add(in.orderItemId())) {
                throw ApiException.badRequest("Un item de la orden esta repetido.");
            }
            BigDecimal pendiente = oi.getQtyOrdered()
                    .subtract(recibidoPrevio.getOrDefault(oi.getOrderItemId(), BigDecimal.ZERO));
            if (in.qtyReceived().compareTo(pendiente) > 0) {
                throw ApiException.badRequest("La cantidad recibida de \"" + oi.getDescription()
                        + "\" supera lo pendiente (" + pendiente.stripTrailingZeros().toPlainString() + ").");
            }
            if (in.conditionStatus() != null && !CONDICIONES.contains(in.conditionStatus())) {
                throw ApiException.badRequest("Condicion invalida para \"" + oi.getDescription() + "\".");
            }
            if (in.incidencia() != null) {
                validarIncidencia(in.incidencia(), oi);
            }
            if (in.qtyReceived().signum() > 0 || in.incidencia() != null) {
                algoQueRegistrar = true;
            }
        }
        if (!algoQueRegistrar) {
            throw ApiException.badRequest("Indica al menos una cantidad recibida o una incidencia.");
        }

        Recepcion recepcion = new Recepcion();
        recepcion.setReceiptId(UUID.randomUUID());
        recepcion.setOrden(orden);
        recepcion.setAlmacen(almacen);
        recepcion.setReceiptNumber(recepcionRepository.maxNumeroPorOrden(orderId) + 1);
        recepcion.setReceiptDate(Instant.now());
        recepcion.setStatus("PARCIAL");  // se ajusta al final
        recepcion.setRegistrationSource(origen);
        recepcion.setObservation(blankToNull(request.observation()));
        recepcion.setReceivedBy(usuario);
        recepcionRepository.save(recepcion);

        registrarGuia(request.guia(), orden, recepcion);

        boolean observada = false;
        for (RecepcionItemInput in : request.items()) {
            OrdenItem oi = itemsOrden.get(in.orderItemId());
            RecepcionItem ri = null;
            if (in.qtyReceived().signum() > 0) {
                ri = new RecepcionItem();
                ri.setReceiptItemId(UUID.randomUUID());
                ri.setRecepcion(recepcion);
                ri.setOrdenItem(oi);
                ri.setQtyReceived(in.qtyReceived());
                ri.setConditionStatus(in.conditionStatus() != null ? in.conditionStatus() : "CONFORME");
                ri.setObservation(blankToNull(in.observation()));
                recepcionItemRepository.save(ri);
                observada |= "OBSERVADO".equals(ri.getConditionStatus());
                ingresarAStock(ri, oi, almacen, usuario, recepcion);
            }
            if (in.incidencia() != null) {
                crearIncidencia(in.incidencia(), recepcion, oi, ri, usuario);
                observada = true;
            }
        }

        Map<UUID, BigDecimal> recibidoAhora = recepcionItemRepository.recibidoPorItem(itemsOrden.keySet());
        boolean ordenCompleta = itemsOrden.values().stream().allMatch(oi ->
                recibidoAhora.getOrDefault(oi.getOrderItemId(), BigDecimal.ZERO).compareTo(oi.getQtyOrdered()) >= 0);
        orden.setStatus(ordenCompleta ? "RECEPCION_COMPLETA" : "RECEPCION_PARCIAL");
        ordenRepository.save(orden);
        recepcion.setStatus(observada ? "OBSERVADA" : (ordenCompleta ? "COMPLETA" : "PARCIAL"));
        recepcionRepository.save(recepcion);
    }

    @Transactional
    public IncidenciaDto actualizarIncidencia(UUID incidenciaId, ActualizarIncidenciaRequest request,
                                              UsuarioPrincipal principal) {
        exigirLogisticaOAdmin(principal);
        IncidenciaRecepcion inc = incidenciaRepository.findById(incidenciaId)
                .orElseThrow(() -> ApiException.notFound("Incidencia no encontrada."));
        if (!ESTADOS_INCIDENCIA.contains(request.status())) {
            throw ApiException.badRequest("Estado de incidencia invalido.");
        }
        if (request.supplierAction() != null && !request.supplierAction().isBlank()
                && !ACCIONES_PROVEEDOR.contains(request.supplierAction())) {
            throw ApiException.badRequest("Accion del proveedor invalida.");
        }
        inc.setStatus(request.status());
        if (request.supplierAction() != null) {
            inc.setSupplierAction(blankToNull(request.supplierAction()));
        }
        inc.setClosedAt("CERRADA".equals(request.status()) ? Instant.now() : null);
        incidenciaRepository.save(inc);
        return AlmacenMapper.toDto(inc);
    }

    private void validarIncidencia(IncidenciaInput inc, OrdenItem oi) {
        if (!TIPOS_INCIDENCIA.contains(inc.issueType())) {
            throw ApiException.badRequest("Tipo de incidencia invalido para \"" + oi.getDescription() + "\".");
        }
        if (inc.supplierAction() != null && !inc.supplierAction().isBlank()
                && !ACCIONES_PROVEEDOR.contains(inc.supplierAction())) {
            throw ApiException.badRequest("Accion del proveedor invalida para \"" + oi.getDescription() + "\".");
        }
    }

    private void registrarGuia(GuiaInput guia, OrdenAbastecimiento orden, Recepcion recepcion) {
        if (guia == null || (isBlank(guia.series()) && isBlank(guia.number()))) {
            return;
        }
        if (isBlank(guia.series()) || isBlank(guia.number()) || guia.issueDate() == null) {
            throw ApiException.badRequest("Para registrar la guia completa serie, numero y fecha.");
        }
        String ruc = orden.getProveedor().getRuc();
        String serie = guia.series().trim().toUpperCase();
        String numero = guia.number().trim();
        if (guiaRepository.existsByIssuerRucAndSeriesAndNumber(ruc, serie, numero)) {
            throw ApiException.badRequest("La guia " + serie + "-" + numero + " ya fue registrada.");
        }
        GuiaRemision g = new GuiaRemision();
        g.setGuideId(UUID.randomUUID());
        g.setOrden(orden);
        g.setRecepcion(recepcion);
        g.setIssuerType("PROVEEDOR");
        g.setIssuerRuc(ruc);
        g.setSeries(serie);
        g.setNumber(numero);
        g.setIssueDate(guia.issueDate());
        guiaRepository.save(g);
    }

    /**
     * ENTRADA en el kardex + reserva para los requerimientos que originaron la
     * compra: lo recibido de un consolidado se reparte entre sus necesidades
     * (critica, urgente, normal; luego la mas antigua). Lo que sobre queda libre.
     */
    private void ingresarAStock(RecepcionItem ri, OrdenItem oi, Almacen almacen, Usuario usuario, Recepcion recepcion) {
        if (oi.getProducto() == null) {
            // Todas las lineas de OC de este backend son producto fisico (V7); defensa por si cambia.
            throw ApiException.badRequest("Solo se reciben en almacen productos fisicos.");
        }
        MovimientoInventario mov = new MovimientoInventario();
        mov.setInventoryMovementId(UUID.randomUUID());
        mov.setEmpresa(recepcion.getOrden().getEmpresa());
        mov.setAlmacen(almacen);
        mov.setProducto(oi.getProducto());
        mov.setVariante(oi.getVariante());
        mov.setMovementType("ENTRADA");
        mov.setQty(ri.getQtyReceived());
        mov.setUnitCost(oi.getUnitCost());
        mov.setReferenceType("RECEPCION");
        mov.setReferenceId(recepcion.getReceiptId());
        mov.setCreatedBy(usuario);
        movimientoRepository.save(mov);

        if (oi.getConsolidacion() == null) {
            // OC previa al Lote 16 sin consolidado (no deberia quedar ninguna tras V20).
            reservar(ri, oi, almacen, oi.getRequerimientoItem(), null, ri.getQtyReceived());
            return;
        }
        List<NecesidadCompra> needs = necesidadRepository.findByConsolidaciones(
                List.of(oi.getConsolidacion().getConsolidationId()));
        Map<UUID, BigDecimal> asignado = new HashMap<>();
        if (!needs.isEmpty()) {
            for (Object[] fila : reservaRepository.sumarRecibidoPorNecesidad(
                    needs.stream().map(NecesidadCompra::getNeedId).toList())) {
                asignado.put((UUID) fila[0], (BigDecimal) fila[1]);
            }
        }
        BigDecimal libre = ri.getQtyReceived();
        for (NecesidadCompra n : needs) {
            if (libre.signum() <= 0) {
                break;
            }
            BigDecimal falta = n.getQty().subtract(asignado.getOrDefault(n.getNeedId(), BigDecimal.ZERO));
            BigDecimal toma = falta.min(libre);
            if (toma.signum() > 0) {
                reservar(ri, oi, almacen, n.getRequerimientoItem(), n, toma);
                libre = libre.subtract(toma);
            }
        }
    }

    private void reservar(RecepcionItem ri, OrdenItem oi, Almacen almacen, RequerimientoItem item,
                          NecesidadCompra necesidad, BigDecimal qty) {
        ReservaStock reserva = new ReservaStock();
        reserva.setReservationId(UUID.randomUUID());
        reserva.setRequerimientoItem(item);
        reserva.setNecesidad(necesidad);
        reserva.setAlmacen(almacen);
        reserva.setProducto(oi.getProducto());
        reserva.setVariante(oi.getVariante());
        reserva.setQtyReserved(qty);
        reserva.setStatus("RESERVADA");
        reserva.setOrigin(ReservaStock.ORIGEN_RECEPCION_OC);
        reserva.setRecepcionItem(ri);
        reservaRepository.save(reserva);
    }

    private void crearIncidencia(IncidenciaInput in, Recepcion recepcion, OrdenItem oi, RecepcionItem ri, Usuario usuario) {
        IncidenciaRecepcion inc = new IncidenciaRecepcion();
        inc.setReceiptIssueId(UUID.randomUUID());
        inc.setRecepcion(recepcion);
        inc.setOrdenItem(oi);
        inc.setRecepcionItem(ri);
        inc.setIssueType(in.issueType());
        inc.setQtyAffected(in.qtyAffected());
        inc.setDescription(in.description().trim());
        inc.setSupplierAction(blankToNull(in.supplierAction()));
        inc.setStatus("ABIERTA");
        inc.setCreatedAt(Instant.now());
        inc.setCreatedBy(usuario);
        incidenciaRepository.save(inc);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
