package com.tdem.logistica.modules.aprobaciones;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.aprobaciones.dto.AprobacionPendienteDto;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/aprobaciones")
public class AprobacionController {

    private final AprobacionService service;

    public AprobacionController(AprobacionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AprobacionPendienteDto>> listarPendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarPendientes(principal));
    }

    @PostMapping("/{approvalId}/decidir")
    public ApiResponse<RequerimientoDto> decidir(
            @PathVariable UUID approvalId,
            @Valid @RequestBody DecidirAprobacionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RequerimientoDto actualizado = service.decidir(approvalId, request, principal);
        String accion = switch (request.decision()) {
            case "APROBADO" -> " aprobado.";
            case "OBSERVADO" -> " observado: vuelve al solicitante.";
            default -> " rechazado.";
        };
        String mensaje = "Requerimiento " + actualizado.code() + accion;
        return ApiResponse.ok(actualizado, mensaje);
    }
}
