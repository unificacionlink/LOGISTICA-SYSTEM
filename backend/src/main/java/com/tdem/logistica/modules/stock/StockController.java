package com.tdem.logistica.modules.stock;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Stock del almacen central del grupo (DEC-L045). Solo LOGISTICA/ADMIN. */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;
    private final CargaStockService carga;

    public StockController(StockService service, CargaStockService carga) {
        this.service = service;
        this.carga = carga;
    }

    @GetMapping("/saldos")
    public ApiResponse<List<SaldoStockDto>> saldos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarSaldos(principal));
    }

    @GetMapping("/variantes/{variantId}/kardex")
    public ApiResponse<KardexDto> kardex(@PathVariable UUID variantId,
                                         @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.kardex(variantId, principal));
    }

    @PostMapping("/ajustes")
    public ApiResponse<SaldoStockDto> ajuste(@Valid @RequestBody AjusteStockRequest request,
                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        SaldoStockDto s = service.registrarAjuste(request, principal);
        return ApiResponse.ok(s, "Stock de " + s.codigoVariante() + " actualizado.");
    }

    @PutMapping("/variantes/{variantId}/minimo")
    public ApiResponse<SaldoStockDto> minimo(@PathVariable UUID variantId, @Valid @RequestBody MinimoStockRequest request,
                                             @AuthenticationPrincipal UsuarioPrincipal principal) {
        SaldoStockDto s = service.fijarMinimo(variantId, request.minimo(), principal);
        return ApiResponse.ok(s, s.minimo() == null ? "Stock minimo quitado." : "Stock minimo actualizado.");
    }

    // ---------- Inventario por Excel ----------

    @GetMapping("/carga/plantilla")
    public ResponseEntity<byte[]> plantilla(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return Descargas.archivo(carga.plantilla(principal), "INVENTARIO_" + LocalDate.now() + ".xlsx", Descargas.XLSX);
    }

    @PostMapping(value = "/carga/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CargaStockDto> validar(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(carga.validar(file, principal));
    }

    @PostMapping(value = "/carga/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CargaStockDto> confirmar(@RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        CargaStockDto r = carga.confirmar(file, principal);
        return ApiResponse.ok(r, "Inventario " + r.codigo() + " aplicado: " + r.ajustes() + " ajustes y "
                + r.minimos() + " minimos actualizados.");
    }
}
