package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping("/saldos")
    public ApiResponse<List<SaldoStockDto>> saldos(
            @RequestParam UUID companyId, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarSaldos(companyId, principal));
    }

    @PostMapping("/ajustes")
    public ApiResponse<SaldoAjustadoDto> ajuste(
            @Valid @RequestBody AjusteStockRequest request, @AuthenticationPrincipal UsuarioPrincipal principal) {
        SaldoAjustadoDto resultado = service.registrarAjuste(request, principal);
        return ApiResponse.ok(resultado, "Stock de " + resultado.codigoVariante() + " actualizado.");
    }

    @GetMapping("/empresas")
    public ApiResponse<List<EmpresaDto>> empresas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarEmpresas(principal));
    }
}
