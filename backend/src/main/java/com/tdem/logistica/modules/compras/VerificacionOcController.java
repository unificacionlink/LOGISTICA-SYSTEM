package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.compras.dto.GuardarVerificacionRequest;
import com.tdem.logistica.modules.compras.dto.VerificacionOcDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** F-LOG-08 de una OC (DEC-L102); los PDF estan en DocumentosController. */
@RestController
public class VerificacionOcController {

    private final VerificacionOcService service;
    private final DespachoService despacho;

    public VerificacionOcController(VerificacionOcService service, DespachoService despacho) {
        this.service = service;
        this.despacho = despacho;
    }

    @GetMapping("/api/compras/ordenes/{id}/verificacion")
    public ApiResponse<VerificacionOcDto> obtener(@PathVariable UUID id,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    @PutMapping("/api/compras/ordenes/{id}/verificacion")
    public ApiResponse<VerificacionOcDto> guardar(@PathVariable UUID id, @Valid @RequestBody GuardarVerificacionRequest request,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.guardar(id, request, principal), "Verificación guardada.");
    }

    /** Las verificaciones de las OC que despacha esta Nota de Inventario; quien ve la nota, las ve. */
    @GetMapping("/api/almacen/notas/{id}/verificaciones")
    public ApiResponse<List<VerificacionOcService.VerificacionDeNota>> deNota(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        despacho.obtenerNota(id, principal);
        return ApiResponse.ok(service.deNota(id));
    }
}
