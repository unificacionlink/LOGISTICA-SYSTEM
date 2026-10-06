package com.tdem.logistica.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Autentica la conexion WebSocket en el handshake HTTP (misma cookie
 * httpOnly que ya usa JwtAuthFilter para REST) — Spring Security HTTP no
 * protege /ws/** (ver SecurityConfig), asi que esta es la unica puerta.
 * Sin cookie valida, rechaza el handshake (mismo criterio que el resto de
 * la API: sin sesion, sin acceso).
 */
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLES = "roles";

    private final JwtService jwtService;

    public WebSocketAuthHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        Optional<String> token = leerCookie(servletRequest.getServletRequest());
        Optional<Claims> claims = token.flatMap(jwtService::validar);
        if (claims.isEmpty()) {
            return false;
        }

        attributes.put(ATTR_USER_ID, claims.get().getSubject());
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get().get("roles", List.class);
        attributes.put(ATTR_ROLES, roles);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private Optional<String> leerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (JwtAuthFilter.COOKIE_NAME.equals(c.getName())) return Optional.of(c.getValue());
        }
        return Optional.empty();
    }
}
