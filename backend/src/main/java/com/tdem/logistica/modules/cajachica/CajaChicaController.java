package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.cajachica.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Caja Chica como ruta de compra del requerimiento (DEC-L026). */
@RestController
@RequestMapping("/api/caja-chica")
public class CajaChicaController {

    public record RegularizarRequest(String codigoExterno) {
    }

    public record ObservarRequest(String motivo) {
    }

    private final CajaChicaService service;

    public CajaChicaController(CajaChicaService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CajaChicaDto>> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(principal));
    }

    @GetMapping("/regla")
    public ApiResponse<ReglaCajaChicaDto> regla(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.reglaCorporativa(principal));
    }

    @PutMapping("/regla")
    public ApiResponse<ReglaCajaChicaDto> guardarRegla(@Valid @RequestBody GuardarReglaRequest request,
                                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.guardarRegla(request, principal), "Regla de Caja Chica actualizada.");
    }

    @GetMapping("/requerimientos/{id}/evaluacion")
    public ApiResponse<EvaluacionCajaChicaDto> evaluar(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.evaluar(id, principal));
    }

    @GetMapping("/requerimientos/{id}")
    public ApiResponse<List<CajaChicaDto>> porRequerimiento(@PathVariable UUID id,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarPorRequerimiento(id, principal));
    }

    @PostMapping("/requerimientos/{id}")
    public ApiResponse<CajaChicaDto> registrar(@PathVariable UUID id, @Valid @RequestBody RegistrarCajaChicaRequest request,
                                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        CajaChicaDto c = service.registrar(id, request, principal);
        return ApiResponse.ok(c, "Compra " + c.codigo() + " registrada: lo comprado ya esta reservado para el despacho.");
    }

    @PostMapping("/{id}/regularizar")
    public ApiResponse<CajaChicaDto> regularizar(@PathVariable UUID id, @RequestBody RegularizarRequest request,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.regularizar(id, request.codigoExterno(), principal), "Compra regularizada.");
    }

    @PostMapping("/{id}/observar")
    public ApiResponse<CajaChicaDto> observar(@PathVariable UUID id, @RequestBody ObservarRequest request,
                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.observar(id, request.motivo(), principal), "Compra observada.");
    }
}
