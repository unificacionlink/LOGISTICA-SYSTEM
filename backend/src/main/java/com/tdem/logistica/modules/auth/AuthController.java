package com.tdem.logistica.modules.auth;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.security.JwtAuthFilter;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, @Value("${app.cookie-secure}") boolean cookieSecure) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UsuarioDto>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult resultado = authService.login(request.correo(), request.password());
        ResponseCookie cookie = ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, resultado.token())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(resultado.expirationSeconds())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(resultado.usuario(), "Sesion iniciada correctamente."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(null, "Sesion cerrada."));
    }

    @PostMapping("/cambiar-password")
    public ApiResponse<UsuarioDto> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest request,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(authService.cambiarPassword(principal.userId(), request.actual(), request.nueva()),
                "Contrasena actualizada.");
    }

    /** El propio usuario termina o salta un recorrido guiado (DEC-L055). */
    @PutMapping("/tutoriales/{codigo}")
    public ApiResponse<UsuarioDto> marcarTutorial(@PathVariable String codigo,
                                                  @Valid @RequestBody TutorialRequest request,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(authService.marcarTutorial(principal.userId(), codigo, request.estado()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioDto>> me(@AuthenticationPrincipal UsuarioPrincipal principal) {
        UsuarioDto dto = authService.me(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }
}
