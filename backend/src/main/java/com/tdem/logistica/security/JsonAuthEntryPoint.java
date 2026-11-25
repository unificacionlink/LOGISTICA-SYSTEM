package com.tdem.logistica.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Responde con el contrato ApiResponse en vez del 403 vacio por defecto de Spring Security. */
@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // DEC-L090: el aviso dice por que se cerro, para no confundirlo con un vencimiento.
        response.getWriter().write(request.getAttribute(JwtAuthFilter.ATR_SESION_REEMPLAZADA) != null
                ? "{\"ok\":false,\"result\":null,\"message\":\"Tu sesión se abrió en otro dispositivo.\","
                        + "\"code\":\"SESION_REEMPLAZADA\",\"error\":null}"
                : "{\"ok\":false,\"result\":null,\"message\":\"Debes iniciar sesion.\",\"code\":\"NO_AUTORIZADO\",\"error\":null}");
    }
}
