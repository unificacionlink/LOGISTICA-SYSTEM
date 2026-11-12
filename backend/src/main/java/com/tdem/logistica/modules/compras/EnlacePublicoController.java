package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.compras.dto.CotizacionPublicaDto;
import com.tdem.logistica.modules.compras.dto.EnviarCotizacionEnlaceRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Paginas publicas del proveedor (DEC-L052): sin sesion, autorizadas solo por
 * el token del enlace. Pasan por {@code LimiteSolicitudes} (por IP).
 */
@RestController
@RequestMapping("/api/publico/cotizacion")
public class EnlacePublicoController {

    private final EnlaceCotizacionService service;

    public EnlacePublicoController(EnlaceCotizacionService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public ApiResponse<CotizacionPublicaDto> obtener(@PathVariable String token) {
        return ApiResponse.ok(service.obtener(token));
    }

    @PostMapping(value = "/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CotizacionPublicaDto> enviar(
            @PathVariable String token,
            @Valid @RequestPart("datos") EnviarCotizacionEnlaceRequest datos,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        return ApiResponse.ok(service.enviar(token, datos, archivo), "Cotizacion enviada. Gracias.");
    }
}
