package com.tdem.logistica.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lee el JWT de la cookie httpOnly y puebla el SecurityContext (equivalente a getSessionUser()). */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "auth_token";

    private final JwtService jwtService;
    private final AccesoVigente accesoVigente;

    public JwtAuthFilter(JwtService jwtService, AccesoVigente accesoVigente) {
        this.jwtService = jwtService;
        this.accesoVigente = accesoVigente;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        leerCookie(request).flatMap(jwtService::validar).ifPresent(claims -> {
            // Roles y estado vigentes (no los del login): un usuario desactivado
            // o con roles cambiados deja de operar sin esperar a que venza el token.
            UUID userId = UUID.fromString(claims.getSubject());
            AccesoVigente.Acceso acceso = accesoVigente.obtener(userId);
            if (!acceso.activo()) {
                return;
            }
            UsuarioPrincipal principal = new UsuarioPrincipal(userId, acceso.roles());
            List<GrantedAuthority> authorities = principal.roles().stream()
                    .map(codigo -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + codigo))
                    .toList();
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        });

        filterChain.doFilter(request, response);
    }

    private Optional<String> leerCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return Optional.of(c.getValue());
        }
        return Optional.empty();
    }
}
