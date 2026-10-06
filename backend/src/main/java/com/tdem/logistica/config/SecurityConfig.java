package com.tdem.logistica.config;

import com.tdem.logistica.security.JsonAuthEntryPoint;
import com.tdem.logistica.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Reemplaza Supabase Auth + RLS (DEC-L008): sesion sin estado via JWT en
 * cookie httpOnly, autorizacion por rol resuelta en cada *Service (ya no en
 * policies de Postgres).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthEntryPoint jsonAuthEntryPoint;
    private final String allowedOrigins;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            JsonAuthEntryPoint jsonAuthEntryPoint,
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jsonAuthEntryPoint = jsonAuthEntryPoint;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonAuthEntryPoint))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/api/auth/login").permitAll()
                        // Assets del SPA cuando el backend los sirve empaquetados (ver Dockerfile);
                        // en desarrollo local estas rutas simplemente no se piden (Vite corre aparte).
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers(
                                "/login", "/inicio", "/requerimientos/**", "/aprobaciones",
                                "/stock", "/stock/ajustes", "/catalogo",
                                "/proveedores", "/proveedores/**", "/compras", "/compras/**",
                                "/almacen", "/almacen/**", "/admin", "/cambiar-password")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * /ws/** (STOMP/SockJS) queda totalmente fuera del filter chain de Spring
     * Security -- no solo permitAll(). La autenticacion real la hace
     * WebSocketAuthHandshakeInterceptor sobre la cookie en el handshake.
     * Necesario: combinar el filter chain de Security (que envuelve la
     * request para soporte async) con el manejo interno de SockJS produce
     * un StackOverflowError recursivo en ApplicationHttpRequest.setAttribute
     * (problema conocido, ver spring-projects/spring-framework#23712).
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/ws/**");
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
