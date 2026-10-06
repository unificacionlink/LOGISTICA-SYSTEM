package com.tdem.logistica.modules.tracking;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EntidadAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.tracking.dto.EntregaDto;
import com.tdem.logistica.modules.tracking.dto.EventoDto;
import com.tdem.logistica.modules.tracking.dto.HitoDto;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;
import com.tdem.logistica.repository.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tracking del requerimiento (RF-039/081/083). No guarda nada: reconstruye
 * hitos, entregas e historial desde las tablas de cada etapa (aprobacion,
 * reserva, RFQ, OC, recepcion, NI, envio), asi nunca se desalinea del flujo.
 */
@Service
@Transactional(readOnly = true)
public class TrackingService {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<String> ENVIO_ENTREGADO = Set.of("RECEPCIONADO", "CERRADO");
    private static final Set<String> ESTADOS_APROBADOS = Set.of(
            EstadoRequerimiento.VERIFICACION_STOCK, EstadoRequerimiento.EN_ATENCION,
            EstadoRequerimiento.ATENCION_PARCIAL, EstadoRequerimiento.ATENCION_COMPLETA);

    private final RequerimientoRepository requerimientoRepository;
    private final RequerimientoItemRepository requerimientoItemRepository;
    private final RequerimientoService requerimientoService;
    private final AprobacionRepository aprobacionRepository;
    private final ReservaStockRepository reservaRepository;
    private final RfqRepository rfqRepository;
    private final OrdenAbastecimientoRepository ordenRepository;
    private final RecepcionRepository recepcionRepository;
    private final NotaInventarioRepository notaRepository;
    private final EnvioRepository envioRepository;
    private final SalidaItemRepository salidaItemRepository;
    private final DocumentoRelacionRepository documentoRelacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public TrackingService(RequerimientoRepository requerimientoRepository,
                           RequerimientoItemRepository requerimientoItemRepository,
                           RequerimientoService requerimientoService,
                           AprobacionRepository aprobacionRepository,
                           ReservaStockRepository reservaRepository,
                           RfqRepository rfqRepository,
                           OrdenAbastecimientoRepository ordenRepository,
                           RecepcionRepository recepcionRepository,
                           NotaInventarioRepository notaRepository,
                           EnvioRepository envioRepository,
                           SalidaItemRepository salidaItemRepository,
                           DocumentoRelacionRepository documentoRelacionRepository,
                           UsuarioRepository usuarioRepository,
                           org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.requerimientoRepository = requerimientoRepository;
        this.requerimientoItemRepository = requerimientoItemRepository;
        this.requerimientoService = requerimientoService;
        this.aprobacionRepository = aprobacionRepository;
        this.reservaRepository = reservaRepository;
        this.rfqRepository = rfqRepository;
        this.ordenRepository = ordenRepository;
        this.recepcionRepository = recepcionRepository;
        this.notaRepository = notaRepository;
        this.envioRepository = envioRepository;
        this.salidaItemRepository = salidaItemRepository;
        this.documentoRelacionRepository = documentoRelacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.jdbc = jdbc;
    }

    public TrackingDto obtener(UUID requestId, UsuarioPrincipal principal) {
        Requerimiento r = requerimientoRepository.findByIdsConDetalle(List.of(requestId)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        requerimientoService.exigirAcceso(r, principal);

        List<RequerimientoItem> items = requerimientoItemRepository.findByRequerimiento(requestId);
        List<Aprobacion> aprobaciones = aprobacionRepository.findPorEntidad(EntidadAprobacion.REQUERIMIENTO, requestId);
        List<NotaInventario> notas = notaRepository.findByRequerimiento(requestId).stream()
                .filter(n -> !"ANULADA".equals(n.getStatus()))
                .toList();
        Map<UUID, Envio> envioPorNota = notas.isEmpty() ? Map.of() : envioRepository
                .findByNotas(notas.stream().map(NotaInventario::getInventoryNoteId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getNotaInventario().getInventoryNoteId(), Function.identity()));
        Map<UUID, List<SalidaItem>> itemsPorSalida = salidaItemRepository.findByRequerimiento(requestId).stream()
                .collect(Collectors.groupingBy(i -> i.getSalida().getIssueId()));

        List<EntregaDto> entregas = entregas(notas, envioPorNota, itemsPorSalida);
        Atencion atencion = calcularAtencion(items, envioPorNota.values(), itemsPorSalida);

        BigDecimal valorPedido = r.getTotalValueNetSnapshot() != null
                ? r.getTotalValueNetSnapshot()
                : notas.stream().map(NotaInventario::getTotalValueSnapshot).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TrackingDto(
                RequerimientoMapper.toDto(r),
                r.getCeco().getCode(),
                r.getScheduledDeliveryDate() != null ? r.getScheduledDeliveryDate() : r.getRequiredDate(),
                r.getDeliveryAddressSnapshot(),
                valorPedido,
                atencion.solicitadas,
                atencion.entregadas,
                atencion.porcentaje(),
                hitos(r, aprobaciones, envioPorNota.values(), atencion),
                entregas,
                eventos(r, aprobaciones, notas, envioPorNota));
    }

    // ------------------------------------------------------------------
    // Entregas (una fila por NI)
    // ------------------------------------------------------------------

    private List<EntregaDto> entregas(List<NotaInventario> notas, Map<UUID, Envio> envioPorNota,
                                      Map<UUID, List<SalidaItem>> itemsPorSalida) {
        // Adjuntos de todos los envios en una consulta; si se subio mas de uno del mismo tipo, vale el ultimo.
        Map<UUID, Map<String, String>> docsPorEnvio = new HashMap<>();
        if (!envioPorNota.isEmpty()) {
            for (DocumentoRelacion rel : documentoRelacionRepository.findDocumentosDe("ENVIO",
                    envioPorNota.values().stream().map(Envio::getShipmentId).toList())) {
                docsPorEnvio.computeIfAbsent(rel.getEntityId(), k -> new HashMap<>())
                        .put(rel.getDocumento().getDocumentType(), rel.getDocumento().getFileUrl());
            }
        }
        List<EntregaDto> filas = new ArrayList<>();
        for (NotaInventario n : notas) {
            Envio e = envioPorNota.get(n.getInventoryNoteId());
            Map<String, String> docs = e == null ? Map.of() : docsPorEnvio.getOrDefault(e.getShipmentId(), Map.of());
            List<SalidaItem> salidaItems = itemsPorSalida.getOrDefault(
                    n.getSalida() != null ? n.getSalida().getIssueId() : null, List.of());
            filas.add(new EntregaDto(
                    filas.size() + 1,
                    n.getInventoryNoteId(),
                    n.getInventoryNoteCode(),
                    n.getIssuedAt(),
                    textoItems(salidaItems),
                    n.getTotalValueSnapshot(),
                    e != null ? e.getShipmentId() : null,
                    e != null ? e.getDeliveryMode() : null,
                    e != null ? e.getCarrierName() : null,
                    e != null ? e.getTrackingCode() : null,
                    e != null ? e.getShipDate() : null,
                    e != null && e.getMoneda() != null ? e.getMoneda().getCurrencyCode() : null,
                    e != null ? e.getShippingCostNet() : null,
                    e != null ? e.getStatus() : "SIN_ENVIO",
                    e != null ? e.getReceivedAt() : null,
                    docs.get("GUIA"),
                    docs.get("TICKET_ENVIO"),
                    docs.get("CONFORMIDAD_ENTREGA")));
        }
        return filas;
    }

    private static String textoItems(List<SalidaItem> items) {
        return items.stream()
                .map(i -> {
                    String codigo = i.getVariante() != null ? i.getVariante().getInternalVariantCode()
                            : i.getProducto().getSkuBase();
                    return codigo + " " + i.getProducto().getName() + " x " + cantidad(i.getQty());
                })
                .collect(Collectors.joining("; "));
    }

    static String cantidad(BigDecimal qty) {
        return qty == null ? "" : qty.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------
    // Atencion: lo entregado en destino, envio por envio (RF-079)
    // ------------------------------------------------------------------

    private record Atencion(BigDecimal solicitadas, BigDecimal entregadas,
                            Instant primeraEntrega, Instant completaEn, boolean completaEnUnaEntrega) {
        int porcentaje() {
            if (solicitadas.signum() == 0) {
                return 0;
            }
            return entregadas.multiply(BigDecimal.valueOf(100))
                    .divide(solicitadas, 0, RoundingMode.DOWN).intValue();
        }

        boolean completa() {
            return completaEn != null;
        }
    }

    private static Atencion calcularAtencion(List<RequerimientoItem> items, Collection<Envio> envios,
                                             Map<UUID, List<SalidaItem>> itemsPorSalida) {
        Map<UUID, BigDecimal> solicitado = new HashMap<>();
        for (RequerimientoItem i : items) {
            solicitado.put(i.getRequestItemId(), i.getQtyRequested());
        }
        List<Envio> entregados = envios.stream()
                .filter(e -> ENVIO_ENTREGADO.contains(e.getStatus()) && e.getReceivedAt() != null)
                .sorted(Comparator.comparing(Envio::getReceivedAt))
                .toList();

        Map<UUID, BigDecimal> entregado = new HashMap<>();
        Instant primera = null;
        Instant completa = null;
        int entregasHastaCompletar = 0;
        for (Envio e : entregados) {
            for (SalidaItem si : itemsPorSalida.getOrDefault(e.getSalida().getIssueId(), List.of())) {
                entregado.merge(si.getRequerimientoItem().getRequestItemId(), si.getQty(), BigDecimal::add);
            }
            if (primera == null) {
                primera = e.getReceivedAt();
            }
            if (completa == null) {
                entregasHastaCompletar++;
                if (!solicitado.isEmpty() && solicitado.entrySet().stream().allMatch(s ->
                        entregado.getOrDefault(s.getKey(), BigDecimal.ZERO).compareTo(s.getValue()) >= 0)) {
                    completa = e.getReceivedAt();
                }
            }
        }

        BigDecimal totalSolicitado = BigDecimal.ZERO;
        BigDecimal totalEntregado = BigDecimal.ZERO;
        for (Map.Entry<UUID, BigDecimal> s : solicitado.entrySet()) {
            totalSolicitado = totalSolicitado.add(s.getValue());
            // Un exceso en un item no compensa lo que falta en otro.
            totalEntregado = totalEntregado.add(entregado.getOrDefault(s.getKey(), BigDecimal.ZERO).min(s.getValue()));
        }
        return new Atencion(totalSolicitado, totalEntregado, primera, completa,
                completa != null && entregasHastaCompletar == 1);
    }

    // ------------------------------------------------------------------
    // Hitos (RF-083)
    // ------------------------------------------------------------------

    private List<HitoDto> hitos(Requerimiento r, List<Aprobacion> aprobaciones, Collection<Envio> envios,
                                Atencion a) {
        boolean enviado = !EstadoRequerimiento.BORRADOR.equals(r.getStatus());
        Instant emitidoEn = aprobaciones.isEmpty() ? instant(r.getCreatedAt())
                : instant(aprobaciones.get(0).getCreatedAt());

        Aprobacion decision = aprobaciones.stream()
                .filter(x -> x.getDecidedAt() != null)
                .reduce((primera, segunda) -> segunda)
                .orElse(null);
        boolean aprobado = ESTADOS_APROBADOS.contains(r.getStatus());
        String detalleAprobacion;
        if (aprobado) {
            detalleAprobacion = decision != null ? "Aprobado por " + nombreUsuario(decision.getDecidedBy())
                    : "Aprobado";
        } else if (EstadoRequerimiento.RECHAZADO.equals(r.getStatus())) {
            detalleAprobacion = "Rechazado" + (decision != null && decision.getDecisionReason() != null
                    ? ": " + decision.getDecisionReason() : "");
        } else if (EstadoRequerimiento.OBSERVADO.equals(r.getStatus())) {
            detalleAprobacion = "Observado" + (decision != null && decision.getDecisionReason() != null
                    ? ": " + decision.getDecisionReason() : "");
        } else {
            detalleAprobacion = enviado ? "Pendiente de aprobación" : "Aún no enviado";
        }

        Instant primeraRecepcion = envios.stream()
                .filter(e -> ENVIO_ENTREGADO.contains(e.getStatus()))
                .map(Envio::getReceivedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        String unidades = cantidad(a.entregadas) + "/" + cantidad(a.solicitadas) + " u.";
        boolean parcial = a.primeraEntrega != null && !a.completaEnUnaEntrega;

        return List.of(
                new HitoDto("EMITIDO", "Emitido", enviado, enviado ? emitidoEn : null,
                        enviado ? "Enviado a aprobación" : "En borrador"),
                new HitoDto("APROBADO", "Aprobado", aprobado,
                        aprobado && decision != null ? instant(decision.getDecidedAt()) : null, detalleAprobacion),
                new HitoDto("RECEPCIONADO", "Recepcionado", primeraRecepcion != null, primeraRecepcion,
                        primeraRecepcion != null ? "Primera entrega recibida en destino" : "Pendiente"),
                new HitoDto("ATENCION_PARCIAL", "Atención parcial", parcial, parcial ? a.primeraEntrega : null,
                        parcial ? unidades : (a.completaEnUnaEntrega ? "Atendido en una sola entrega" : "Pendiente")),
                new HitoDto("ATENCION_COMPLETA", "Atención completa", a.completa(), a.completaEn,
                        a.completa() ? unidades : (a.primeraEntrega != null ? unidades : "Pendiente")));
    }

    // ------------------------------------------------------------------
    // Historial (RF-039)
    // ------------------------------------------------------------------

    private List<EventoDto> eventos(Requerimiento r, List<Aprobacion> aprobaciones, List<NotaInventario> notas,
                                    Map<UUID, Envio> envioPorNota) {
        List<EventoDto> ev = new ArrayList<>();
        String solicitante = r.getRequester() != null ? r.getRequester().getDisplayName() : null;
        ev.add(new EventoDto(instant(r.getCreatedAt()), "Requerimiento", "Creado", solicitante, r.getRequestCode()));

        for (int i = 0; i < aprobaciones.size(); i++) {
            Aprobacion a = aprobaciones.get(i);
            ev.add(new EventoDto(instant(a.getCreatedAt()), "Aprobación",
                    i == 0 ? "Enviado a aprobación" : "Reenviado a aprobación", solicitante, null));
            if (a.getDecidedAt() != null) {
                String accion = switch (a.getStatus()) {
                    case EstadoAprobacion.APROBADO -> "Aprobado";
                    case EstadoAprobacion.RECHAZADO -> "Rechazado";
                    case EstadoAprobacion.OBSERVADO -> "Observado";
                    default -> a.getStatus();
                };
                String motivo = a.getDecisionReason() != null ? a.getDecisionReason() : a.getComment();
                ev.add(new EventoDto(instant(a.getDecidedAt()), "Aprobación",
                        motivo != null ? accion + ": " + motivo : accion, nombreUsuario(a.getDecidedBy()), null));
            }
        }

        List<ReservaStock> reservas = reservaRepository.findDeStockPorRequerimiento(r.getRequestId());
        if (!reservas.isEmpty()) {
            BigDecimal total = reservas.stream().map(ReservaStock::getQtyReserved).reduce(BigDecimal.ZERO, BigDecimal::add);
            ev.add(new EventoDto(instant(reservas.get(0).getReservedAt()), "Stock",
                    "Reservado del almacén: " + cantidad(total) + " u.", "Sistema", null));
        }

        for (Rfq q : rfqRepository.findByRequestIdOrderByCreatedAtDesc(r.getRequestId())) {
            ev.add(new EventoDto(q.getCreatedAt(), "Compras", "RFQ a " + q.getProveedor().getLegalName(),
                    q.getCreatedBy().getDisplayName(), q.getRfqCode()));
        }

        // Compras por Caja Chica (DEC-L026): entran directo al almacen, reservadas para el REQ.
        jdbc.query("""
                select c.created_at, c.petty_cash_code, c.supplier_name, c.currency_code, c.amount, c.regularization_status,
                  u.display_name
                from caja_chica_ref c join usuario u on u.user_id = c.purchase_user_id
                where c.request_id = ? and c.regularization_status <> 'ANULADO'""", rs -> {
            ev.add(new EventoDto(rs.getTimestamp(1).toInstant(), "Compras",
                    "Caja Chica en " + rs.getString(3) + " (" + rs.getString(4) + " " + rs.getBigDecimal(5).toPlainString() + ")",
                    rs.getString(7), rs.getString(2)));
        }, r.getRequestId());

        List<OrdenAbastecimiento> ordenes = ordenRepository.findByRequerimiento(r.getRequestId());
        for (OrdenAbastecimiento o : ordenes) {
            String accion = "OC a " + o.getProveedor().getLegalName()
                    + ("ANULADA".equals(o.getStatus()) ? " (anulada)" : "");
            ev.add(new EventoDto(o.getCreatedAt(), "Compras", accion, o.getCreatedBy().getDisplayName(), o.getOrderCode()));
        }
        if (!ordenes.isEmpty()) {
            for (Recepcion rc : recepcionRepository.findByOrdenes(
                    ordenes.stream().map(OrdenAbastecimiento::getOrderId).toList())) {
                ev.add(new EventoDto(rc.getReceiptDate(), "Almacén",
                        "Recepción " + rc.getReceiptNumber() + " de la OC (" + rc.getStatus() + ")",
                        rc.getReceivedBy().getDisplayName(), rc.getOrden().getOrderCode()));
            }
        }

        for (NotaInventario n : notas) {
            ev.add(new EventoDto(n.getIssuedAt(), "Despacho",
                    "Nota de Inventario emitida (" + cantidad(n.getTotalItemQty()) + " u.)",
                    n.getPickingUser().getDisplayName(), n.getInventoryNoteCode()));
            Envio e = envioPorNota.get(n.getInventoryNoteId());
            if (e == null) {
                continue;
            }
            String via = e.getCarrierName() != null ? e.getDeliveryMode() + " - " + e.getCarrierName() : e.getDeliveryMode();
            ev.add(new EventoDto(e.getCreatedAt(), "Envío", "Enviado por " + via,
                    e.getCreatedBy().getDisplayName(), n.getInventoryNoteCode()));
            if (e.getReceivedAt() != null) {
                String obs = e.getDeliveryObservation() != null ? ": " + e.getDeliveryObservation() : "";
                ev.add(new EventoDto(e.getReceivedAt(), "Entrega", "Recepcionado en destino" + obs,
                        e.getReceivedBy() != null ? e.getReceivedBy().getDisplayName() : null, n.getInventoryNoteCode()));
            }
        }

        eventosAuditados(r, ordenes, ev);

        ev.sort(Comparator.comparing(EventoDto::fecha, Comparator.nullsLast(Comparator.naturalOrder())));
        return ev;
    }

    /** Programacion de entrega (RF-068) y motivo de anulacion de OC (RF-064), desde log_cambios. */
    private void eventosAuditados(Requerimiento r, List<OrdenAbastecimiento> ordenes, List<EventoDto> ev) {
        Map<UUID, String> codigoOrden = ordenes.stream()
                .collect(Collectors.toMap(OrdenAbastecimiento::getOrderId, OrdenAbastecimiento::getOrderCode));
        List<Object> ids = new ArrayList<>();
        ids.add(r.getRequestId());
        ids.addAll(codigoOrden.keySet());
        String marcadores = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.query("""
                select l.created_at, l.entidad, l.entidad_id, l.accion, l.despues ->> 'fecha', l.despues ->> 'motivo',
                  u.display_name
                from log_cambios l left join usuario u on u.user_id = l.usuario_id
                where (l.entidad = 'REQUERIMIENTO' and l.accion = 'PROGRAMAR_ENTREGA'
                    or l.entidad = 'ORDEN_ABASTECIMIENTO' and l.accion = 'ANULAR')
                  and l.entidad_id in (""" + marcadores + ")", rs -> {
            Instant fecha = rs.getTimestamp(1).toInstant();
            String usuario = rs.getString(7);
            if ("REQUERIMIENTO".equals(rs.getString(2))) {
                LocalDate programada = LocalDate.parse(rs.getString(5));
                ev.add(new EventoDto(fecha, "Entrega", "Entrega programada para el " + programada.format(DD_MM_YYYY),
                        usuario, null));
            } else {
                ev.add(new EventoDto(fecha, "Compras", "OC anulada: " + rs.getString(6), usuario,
                        codigoOrden.get(rs.getObject(3, UUID.class))));
            }
        }, ids.toArray());
    }

    private String nombreUsuario(UUID userId) {
        if (userId == null) {
            return null;
        }
        return usuarioRepository.findById(userId).map(Usuario::getDisplayName).orElse(null);
    }

    private static Instant instant(OffsetDateTime t) {
        return t != null ? t.toInstant() : null;
    }
}
