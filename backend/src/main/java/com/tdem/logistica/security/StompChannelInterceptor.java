package com.tdem.logistica.security;

import com.tdem.logistica.common.AppConstants.Roles;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Autoriza SUBSCRIBE a los topics restringidos por rol (mismo chequeo que AprobacionService en REST). */
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if ("/topic/aprobaciones".equals(destination) && !tieneRol(accessor, APROBADORES)) {
                throw new SecurityException("No tienes permiso para suscribirte a " + destination);
            }
            if ("/topic/ordenes-compra".equals(destination) && !tieneRol(accessor, DECISORES_OC)) {
                throw new SecurityException("No tienes permiso para suscribirte a " + destination);
            }
        }
        return message;
    }

    private static final List<String> APROBADORES = List.of(Roles.APROBADOR, Roles.GERENCIA, Roles.ADMIN);
    /** Mismo chequeo que OrdenCompraService.esDecisorDeOrden (DEC-L061). */
    private static final List<String> DECISORES_OC = List.of(Roles.GERENCIA, Roles.LOGISTICA, Roles.ADMIN);

    @SuppressWarnings("unchecked")
    private boolean tieneRol(StompHeaderAccessor accessor, List<String> permitidos) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return false;
        List<String> roles = (List<String>) attrs.get(WebSocketAuthHandshakeInterceptor.ATTR_ROLES);
        return roles != null && roles.stream().anyMatch(permitidos::contains);
    }
}
