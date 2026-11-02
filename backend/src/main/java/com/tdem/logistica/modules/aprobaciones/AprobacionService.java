package com.tdem.logistica.modules.aprobaciones;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EntidadAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoAprobacion;
import com.tdem.logistica.common.AppConstants.EstadoRequerimiento;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Aprobacion;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.aprobaciones.dto.AprobacionPendienteDto;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.requerimientos.RequerimientoMapper;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.modules.stock.StockService;
import com.tdem.logistica.realtime.RealtimeNotifier;
import com.tdem.logistica.repository.AprobacionRepository;
import com.tdem.logistica.repository.RequerimientoRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AprobacionService {

    private static final List<String> DECISIONES_VALIDAS =
            List.of(EstadoAprobacion.APROBADO, EstadoAprobacion.OBSERVADO, EstadoAprobacion.RECHAZADO);

    private final AprobacionRepository aprobacionRepository;
    private final RequerimientoRepository requerimientoRepository;
    private final RealtimeNotifier realtimeNotifier;
    private final UsuarioRepository usuarioRepository;
    private final StockService stockService;
    private final ComprasService comprasService;
    private final AmbitoAprobacion ambito;

    public AprobacionService(
            AprobacionRepository aprobacionRepository,
            RequerimientoRepository requerimientoRepository,
            RealtimeNotifier realtimeNotifier,
            UsuarioRepository usuarioRepository,
            StockService stockService,
            ComprasService comprasService,
            AmbitoAprobacion ambito) {
        this.aprobacionRepository = aprobacionRepository;
        this.requerimientoRepository = requerimientoRepository;
        this.realtimeNotifier = realtimeNotifier;
        this.usuarioRepository = usuarioRepository;
        this.stockService = stockService;
        this.comprasService = comprasService;
        this.ambito = ambito;
    }

    private void exigirAprobadorOAdmin(UsuarioPrincipal principal) {
        if (!AmbitoAprobacion.esDecisor(principal)) {
            throw ApiException.forbidden("No tienes permiso para gestionar aprobaciones.");
        }
    }

    public List<AprobacionPendienteDto> listarPendientes(UsuarioPrincipal principal) {
        exigirAprobadorOAdmin(principal);

        List<Aprobacion> pendientes = aprobacionRepository.findPendientesPorTipo(EntidadAprobacion.REQUERIMIENTO);
        List<UUID> requestIds = pendientes.stream().map(Aprobacion::getEntityId).toList();
        Map<UUID, Requerimiento> porId = requerimientoRepository.findByIdsConDetalle(requestIds).stream()
                .collect(Collectors.toMap(Requerimiento::getRequestId, r -> r));
        // DEC-L030: solo lo que a este usuario le toca decidir.
        Set<UUID> misCecos = principal.tieneAlgunRol(Roles.GERENCIA) ? ambito.cecosDe(principal.userId()) : Set.of();

        return pendientes.stream()
                .filter(a -> porId.containsKey(a.getEntityId()))
                .filter(a -> ambito.puedeDecidir(a, porId.get(a.getEntityId()), principal, misCecos))
                .map(a -> new AprobacionPendienteDto(a.getApprovalId(), RequerimientoMapper.toDto(porId.get(a.getEntityId()))))
                .toList();
    }

    @Transactional
    public RequerimientoDto decidir(UUID approvalId, DecidirAprobacionRequest request, UsuarioPrincipal principal) {
        exigirAprobadorOAdmin(principal);

        if (!DECISIONES_VALIDAS.contains(request.decision())) {
            throw ApiException.badRequest("Decision invalida.");
        }
        boolean esRechazo = EstadoAprobacion.RECHAZADO.equals(request.decision());
        boolean esObservacion = EstadoAprobacion.OBSERVADO.equals(request.decision());
        if ((esRechazo || esObservacion) && (request.decisionReason() == null || request.decisionReason().isBlank())) {
            throw ApiException.badRequest(esRechazo
                    ? "El motivo es obligatorio para rechazar."
                    : "El motivo es obligatorio para observar.");
        }

        Aprobacion aprobacion = aprobacionRepository.findById(approvalId)
                .orElseThrow(() -> ApiException.notFound("Aprobacion no encontrada."));
        if (!EstadoAprobacion.PENDIENTE.equals(aprobacion.getStatus())) {
            throw ApiException.badRequest("Esta aprobacion ya fue decidida.");
        }

        Requerimiento requerimiento = requerimientoRepository.findById(aprobacion.getEntityId())
                .orElseThrow(() -> ApiException.notFound("Requerimiento no encontrado."));
        if (!EstadoRequerimiento.PENDIENTE_APROBACION.equals(requerimiento.getStatus())) {
            throw ApiException.badRequest("El requerimiento ya no esta pendiente de aprobacion.");
        }
        if (requerimiento.getRequester() != null && requerimiento.getRequester().getUserId().equals(principal.userId())) {
            throw ApiException.forbidden("No puedes decidir tu propio requerimiento.");
        }
        if (!ambito.puedeDecidir(aprobacion, requerimiento, principal)) {
            throw ApiException.forbidden("Este requerimiento lo decide el aprobador asignado a su CECO.");
        }

        aprobacion.setStatus(request.decision());
        aprobacion.setDecidedBy(principal.userId());
        aprobacion.setDecidedAt(OffsetDateTime.now());
        aprobacion.setComment(request.comment());
        aprobacion.setDecisionReason(request.decisionReason() != null ? request.decisionReason().trim() : null);
        aprobacionRepository.save(aprobacion);

        if (esRechazo) {
            requerimiento.setStatus(EstadoRequerimiento.RECHAZADO);
        } else if (esObservacion) {
            // RF-006: vuelve al solicitante; al reenviarlo se crea otra aprobacion PENDIENTE.
            requerimiento.setStatus(EstadoRequerimiento.OBSERVADO);
        } else {
            // DEC-L018: sin verificacion manual. Se aparta lo disponible (RF-009),
            // el faltante se cotiza solo al proveedor habitual (RF-010) y el REQ
            // queda en atencion. PARCIAL/COMPLETA salen de las entregas (RF-079).
            Usuario aprobador = usuarioRepository.findById(principal.userId())
                    .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
            stockService.reservarDisponible(requerimiento);
            comprasService.crearRfqsAutomaticas(requerimiento, aprobador);
            requerimiento.setStatus(EstadoRequerimiento.EN_ATENCION);
        }
        requerimientoRepository.save(requerimiento);

        RequerimientoDto dto = RequerimientoMapper.toDto(requerimiento);
        if (requerimiento.getRequester() != null) {
            realtimeNotifier.decisionTomada(requerimiento.getRequester().getUserId(), dto);
        }
        return dto;
    }
}
