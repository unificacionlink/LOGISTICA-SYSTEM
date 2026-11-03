package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.requerimientos.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/requerimientos")
public class RequerimientoController {

    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RequerimientoService service;

    public RequerimientoController(RequerimientoService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<RequerimientoDto>> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(principal));
    }

    @GetMapping("/catalogo")
    public ApiResponse<CatalogoFormularioDto> catalogo(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtenerDatosFormulario(principal));
    }

    @GetMapping("/{id}")
    public ApiResponse<RequerimientoDetalleDto> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    @PostMapping
    public ApiResponse<RequerimientoDto> crear(
            @Valid @RequestBody CrearRequerimientoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RequerimientoDto creado = service.crear(request, principal);
        return ApiResponse.ok(creado, "Requerimiento " + creado.code() + " creado en BORRADOR.");
    }

    @PutMapping("/{id}")
    public ApiResponse<RequerimientoDto> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CrearRequerimientoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RequerimientoDto actualizado = service.actualizar(id, request, principal);
        return ApiResponse.ok(actualizado, "Requerimiento " + actualizado.code() + " actualizado.");
    }

    /** Justificacion de la excepcion presupuestal (DEC-L038); opcional. */
    public record EnviarRequest(@Size(max = 1000) String justificacionPresupuestal) {
    }

    @PostMapping("/{id}/enviar")
    public ApiResponse<RequerimientoDto> enviar(
            @PathVariable UUID id, @Valid @RequestBody(required = false) EnviarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RequerimientoDto enviado = service.enviar(id, request == null ? null : request.justificacionPresupuestal(),
                principal);
        return ApiResponse.ok(enviado, "Requerimiento " + enviado.code() + " enviado a aprobacion.");
    }

    @PutMapping("/{id}/programacion")
    public ApiResponse<RequerimientoDto> programarEntrega(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramarEntregaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RequerimientoDto r = service.programarEntrega(id, request.fecha(), principal);
        return ApiResponse.ok(r, "Entrega programada para el " + r.scheduledDeliveryDate().format(DD_MM_YYYY) + ".");
    }

    @PostMapping(value = "/productos/{productId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> subirImagenProducto(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        String url = service.subirImagenProducto(productId, file, principal);
        return ApiResponse.ok(url, "Imagen del producto actualizada.");
    }
}
