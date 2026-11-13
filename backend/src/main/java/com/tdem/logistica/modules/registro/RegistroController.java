package com.tdem.logistica.modules.registro;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.registro.dto.RegistroDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Registro de la plataforma (DEC-L060): vistas del administrador y reporte de errores del navegador. */
@RestController
public class RegistroController {

    private final RegistroService service;

    public RegistroController(RegistroService service) {
        this.service = service;
    }

    public record ErrorNavegadorRequest(
            @NotBlank @Size(max = 2_000) String mensaje,
            @Size(max = 8_000) String pila,
            @Size(max = 500) String ruta) {
    }

    @PostMapping("/api/registro/errores")
    public ApiResponse<Void> reportarError(@Valid @RequestBody ErrorNavegadorRequest r, HttpServletRequest http,
                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        service.reportarErrorNavegador(r.mensaje(), r.pila(), r.ruta(), http.getHeader("User-Agent"), principal);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/registro/resumen")
    public ApiResponse<RegistroDto.Resumen> resumen(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.resumen(principal));
    }

    @GetMapping("/api/admin/registro/actividad")
    public ApiResponse<RegistroDto.Pagina<RegistroDto.Actividad>> actividad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.actividad(new RegistroService.Filtros(desde, hasta, usuario, q, tipo, pagina), principal));
    }

    @GetMapping("/api/admin/registro/errores")
    public ApiResponse<RegistroDto.Pagina<RegistroDto.Error>> errores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.errores(new RegistroService.Filtros(desde, hasta, usuario, q, tipo, pagina), principal));
    }

    @GetMapping("/api/admin/registro/cambios")
    public ApiResponse<RegistroDto.Pagina<RegistroDto.Cambio>> cambios(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID usuario,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.cambios(new RegistroService.Filtros(desde, hasta, usuario, q, null, pagina), principal));
    }

    @GetMapping("/api/admin/registro/movimientos")
    public ApiResponse<RegistroDto.Pagina<RegistroDto.Movimiento>> movimientos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.movimientos(new RegistroService.Filtros(desde, hasta, usuario, q, tipo, pagina), principal));
    }
}
