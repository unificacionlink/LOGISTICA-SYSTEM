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
            if ("/topic/aprobaciones".equals(destination) && !tieneRolAprobador(accessor)) {
                throw new SecurityException("No tienes permiso para suscribirte a " + destination);
            }
        }
        return message;
    }

    @SuppressWarnings("unchecked")
    private boolean tieneRolAprobador(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return false;
        List<String> roles = (List<String>) attrs.get(WebSocketAuthHandshakeInterceptor.ATTR_ROLES);
        return roles != null && (roles.contains(Roles.APROBADOR) || roles.contains(Roles.ADMIN));
    }
}
