package com.tdem.logistica.modules.registro;

import com.tdem.logistica.config.LimiteSolicitudes;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Anota en el registro de la plataforma (DEC-L060) cada operacion que cambia
 * datos bajo /api (POST, PUT, PATCH, DELETE), con su resultado. Las lecturas
 * no se anotan (serian la gran mayoria y no cambian nada).
 *
 * <p>Corre dentro de la cadena de Spring Security (orden por defecto, despues
 * de ella), asi el usuario de la sesion ya esta en el contexto.
 */
@Component
public class RegistroActividadFilter extends OncePerRequestFilter {

    private static final Set<String> CAMBIAN = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** El reporte de errores del navegador ya queda en registro_error. */
    private static final String ERRORES_NAVEGADOR = "/api/registro/errores";

    private final RegistroPlataforma registro;

    public RegistroActividadFilter(RegistroPlataforma registro) {
        this.registro = registro;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !ruta.startsWith("/api/") || !CAMBIAN.contains(request.getMethod()) || ERRORES_NAVEGADOR.equals(ruta);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long inicio = System.nanoTime();
        boolean fallo = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            fallo = true;
            throw e;
        } finally {
            try {
                String ruta = request.getRequestURI();
                registro.actividad(new RegistroPlataforma.Actividad(
                        Instant.now(),
                        usuario(request),
                        request.getMethod(),
                        RegistroPlataforma.corto(ruta, 300),
                        RegistroPlataforma.modulo(ruta),
                        RegistroPlataforma.entidad(ruta),
                        fallo ? 500 : response.getStatus(),
                        (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - inicio) / 1_000_000),
                        RegistroPlataforma.corto(LimiteSolicitudes.ip(request), 64),
                        RegistroPlataforma.corto(request.getHeader("User-Agent"), 300),
                        RegistroPlataforma.corto(texto(request.getAttribute(RegistroPlataforma.ATR_DETALLE)), 300)));
            } catch (RuntimeException ignorado) {
                // Registrar nunca debe cambiar la respuesta.
            }
        }
    }

    private static UUID usuario(HttpServletRequest request) {
        if (request.getAttribute(RegistroPlataforma.ATR_USUARIO) instanceof UUID id) {
            return id;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UsuarioPrincipal p ? p.userId() : null;
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }
}
