package com.tdem.logistica.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tope de solicitudes por IP y minuto para las rutas publicas
 * ({@code /api/publico/**}, DEC-L052): frena que se prueben tokens a ciegas o
 * se inunde el enlace del proveedor. En memoria (un solo servicio en Render).
 */
@Component
public class LimiteSolicitudes extends OncePerRequestFilter {

    private final int porMinuto;
    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    private record Ventana(long minuto, AtomicInteger cuenta) {
    }

    public LimiteSolicitudes(@Value("${app.publico.limite-por-minuto:30}") int porMinuto) {
        this.porMinuto = porMinuto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/publico/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minuto = System.currentTimeMillis() / 60_000;
        if (ventanas.size() > 10_000) {
            ventanas.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
        }
        Ventana v = ventanas.compute(ip(request), (k, actual) ->
                actual == null || actual.minuto() != minuto ? new Ventana(minuto, new AtomicInteger()) : actual);
        if (v.cuenta().incrementAndGet() > porMinuto) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"ok\":false,\"result\":null,\"message\":"
                    + "\"Demasiados intentos. Espera un minuto y vuelve a intentar.\",\"code\":\"LIMITE\",\"error\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * El proxy de Render agrega al final de X-Forwarded-For la IP que vio; lo
     * anterior lo puede escribir el propio cliente, asi que se toma el ultimo.
     */
    public static String ip(HttpServletRequest request) {
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String[] partes = reenviada.split(",");
            return partes[partes.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
