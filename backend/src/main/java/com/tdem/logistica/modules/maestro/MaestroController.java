package com.tdem.logistica.modules.maestro;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.maestro.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Maestro de productos y solicitudes de alta (Lote 8b, DEC-L025; RF-004). */
@RestController
@RequestMapping("/api/maestro")
public class MaestroController {

    public record EditarProductoRequest(String nombre, UUID uomId, Boolean activo) {
    }

    private final MaestroService service;

    public MaestroController(MaestroService service) {
        this.service = service;
    }

    @GetMapping("/clasificacion")
    public ApiResponse<ClasificacionDto> clasificacion() {
        return ApiResponse.ok(service.clasificacion());
    }

    @PostMapping("/productos")
    public ApiResponse<ProductoCreadoDto> crearProducto(@Valid @RequestBody CrearProductoRequest request,
                                                       @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProductoCreadoDto p = service.crearProducto(request, principal);
        return ApiResponse.ok(p, "Producto creado: " + p.skuBase() + ".");
    }

    @PatchMapping("/productos/{id}")
    public ApiResponse<ProductoCreadoDto> editarProducto(@PathVariable UUID id, @RequestBody EditarProductoRequest request,
                                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.editarProducto(id, request.nombre(), request.uomId(), request.activo(), principal),
                "Producto actualizado.");
    }

    @PostMapping("/productos/{id}/variantes")
    public ApiResponse<ProductoCreadoDto> agregarVariante(@PathVariable UUID id, @RequestBody VarianteInput request,
                                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.agregarVariante(id, request, principal), "Variante agregada.");
    }

    @GetMapping("/solicitudes")
    public ApiResponse<List<SolicitudProductoDto>> solicitudes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarSolicitudes(principal));
    }

    @PostMapping("/solicitudes")
    public ApiResponse<SolicitudProductoDto> crearSolicitud(@Valid @RequestBody CrearSolicitudRequest request,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        SolicitudProductoDto s = service.crearSolicitud(request, principal);
        return ApiResponse.ok(s, "Solicitud " + s.codigo() + " enviada a Logistica.");
    }

    @PostMapping("/solicitudes/{id}/aprobar")
    public ApiResponse<SolicitudProductoDto> aprobar(@PathVariable UUID id, @Valid @RequestBody AprobarSolicitudRequest request,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        SolicitudProductoDto s = service.aprobarSolicitud(id, request, principal);
        return ApiResponse.ok(s, "Producto creado: " + s.skuGenerado() + ".");
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public ApiResponse<SolicitudProductoDto> rechazar(@PathVariable UUID id, @Valid @RequestBody RechazarSolicitudRequest request,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.rechazarSolicitud(id, request.motivo(), principal), "Solicitud rechazada.");
    }
}
