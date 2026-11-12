package com.tdem.logistica.realtime;

import com.tdem.logistica.modules.aprobaciones.dto.AprobacionPendienteDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Punto unico desde donde se publican eventos en tiempo real (WebSocket/STOMP)
 * — evita esparcir SimpMessagingTemplate por los *Service de negocio.
 */
@Component
public class RealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * REQ sin aprobador asignado: avisa a todos los APROBADOR/GERENCIA/ADMIN
     * conectados (autorizado en el SUBSCRIBE, ver StompChannelInterceptor).
     */
    public void nuevaAprobacionPendiente(AprobacionPendienteDto dto) {
        messagingTemplate.convertAndSend("/topic/aprobaciones", dto);
    }

    /** REQ con aprobador asignado (DEC-L030): solo a ese usuario. */
    public void aprobacionAsignada(UUID aprobadorUserId, AprobacionPendienteDto dto) {
        messagingTemplate.convertAndSendToUser(aprobadorUserId.toString(), "/queue/aprobaciones", dto);
    }

    /** Aviso de que un proveedor respondio por su enlace (DEC-L052). */
    public record CotizacionRecibida(UUID rfqId, String rfqCode, String proveedor) {
    }

    /** Solo a quien genero el enlace: es quien sigue esa solicitud. */
    public void cotizacionPorEnlace(UUID userId, CotizacionRecibida dto) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/cotizaciones", dto);
    }

    /** Avisa solo al dueno del requerimiento (cualquier pestana suya conectada). */
    public void decisionTomada(UUID requesterUserId, RequerimientoDto dto) {
        messagingTemplate.convertAndSendToUser(requesterUserId.toString(), "/queue/mis-requerimientos", dto);
    }
}
