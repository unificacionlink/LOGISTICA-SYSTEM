package com.tdem.logistica.config;

import com.tdem.logistica.security.StompChannelInterceptor;
import com.tdem.logistica.security.StompPrincipal;
import com.tdem.logistica.security.WebSocketAuthHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Notificaciones en tiempo real (WebSockets/STOMP) — decision explicita del
 * usuario sobre la alternativa mas liviana (SSE) para dejar el canal
 * bidireccional listo a futuro. El clic de aprobar/rechazar sigue siendo
 * HTTP normal (AprobacionController); esto solo avisa a los DEMAS clientes
 * conectados que algo cambio, sin que tengan que refrescar.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthHandshakeInterceptor authHandshakeInterceptor;
    private final StompChannelInterceptor stompChannelInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthHandshakeInterceptor authHandshakeInterceptor,
            StompChannelInterceptor stompChannelInterceptor,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sin .withSockJS(): esa combinacion (SockJS + Tomcat 11 + Spring
        // Framework 7, version de esta app) produce un StackOverflowError
        // recursivo en ApplicationHttpRequest.setAttribute (bug conocido de
        // esta combinacion de versiones). WebSocket nativo alcanza sobrado
        // para navegadores modernos, sin ese fallback.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .setHandshakeHandler(new UsuarioHandshakeHandler())
                .addInterceptors(authHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }

    /** Toma el userId que dejo WebSocketAuthHandshakeInterceptor y lo convierte en el Principal de la sesion STOMP. */
    private static class UsuarioHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String userId = (String) attributes.get(WebSocketAuthHandshakeInterceptor.ATTR_USER_ID);
            return new StompPrincipal(userId);
        }
    }
}
