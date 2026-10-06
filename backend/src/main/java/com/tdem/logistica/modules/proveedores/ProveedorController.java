package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.proveedores.dto.GuardarProveedorRequest;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService service;

    public ProveedorController(ProveedorService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProveedorDto>> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(principal));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProveedorDto> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    @PostMapping
    public ApiResponse<ProveedorDto> crear(
            @Valid @RequestBody GuardarProveedorRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto creado = service.crear(request, principal);
        return ApiResponse.ok(creado, "Proveedor " + creado.legalName() + " creado.");
    }

    @PutMapping("/{id}")
    public ApiResponse<ProveedorDto> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody GuardarProveedorRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto actualizado = service.actualizar(id, request, principal);
        return ApiResponse.ok(actualizado, "Proveedor " + actualizado.legalName() + " actualizado.");
    }
}
