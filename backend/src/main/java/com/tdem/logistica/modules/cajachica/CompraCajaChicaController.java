package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.common.MotivoRequest;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.cajachica.dto.CompraCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.ConsolidadoCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.LecturaComprobanteDto;
import com.tdem.logistica.modules.cajachica.dto.RegistrarCompraCajaChicaRequest;
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

/** Caja chica como modulo (DEC-L092): registro del solicitante y consolidado. */
@RestController
@RequestMapping("/api/caja-chica")
public class CompraCajaChicaController {

    private final CompraCajaChicaService service;
    private final LecturaComprobanteService lectura;

    public CompraCajaChicaController(CompraCajaChicaService service, LecturaComprobanteService lectura) {
        this.service = service;
        this.lectura = lectura;
    }

    /** DEC-L095: lee por OCR lo que pueda del comprobante; no guarda nada. */
    @PostMapping(value = "/compras/leer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LecturaComprobanteDto> leer(@RequestPart("file") MultipartFile archivo,
                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(lectura.leer(archivo, principal));
    }

    /** Multipart: {@code datos} (JSON) y la factura en {@code file}, o su enlace en datos.facturaUrl. */
    @PostMapping(value = "/compras", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CompraCajaChicaDto> registrar(
            @Valid @RequestPart("datos") RegistrarCompraCajaChicaRequest datos,
            @RequestPart(value = "file", required = false) MultipartFile factura,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.registrar(datos, factura, principal), "Compra registrada en caja chica.");
    }

    @GetMapping("/compras/mias")
    public ApiResponse<List<CompraCajaChicaDto>> mias(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.mias(principal));
    }

    @PostMapping("/compras/{id}/anular")
    public ApiResponse<CompraCajaChicaDto> anular(@PathVariable UUID id, @Valid @RequestBody MotivoRequest request,
                                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.anular(id, request.motivo(), principal), "Compra anulada.");
    }

    @GetMapping("/consolidado")
    public ApiResponse<ConsolidadoCajaChicaDto> consolidado(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) UUID ceco,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.consolidado(desde, hasta, empresa, ceco, principal));
    }

    @GetMapping("/consolidado/excel")
    public ResponseEntity<byte[]> consolidadoExcel(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) UUID ceco,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        byte[] xlsx = CajaChicaExcel.generar(service.paraExcel(desde, hasta, empresa, ceco, principal));
        return Descargas.archivo(xlsx, "CAJA_CHICA_" + LocalDate.now() + ".xlsx", Descargas.XLSX);
    }
}
