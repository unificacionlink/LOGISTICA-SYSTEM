package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.common.MotivoRequest;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/compras")
public class ComprasController {

    private final ComprasService service;

    public ComprasController(ComprasService service) {
        this.service = service;
    }

    @GetMapping("/pendientes")
    public ApiResponse<List<RequerimientoDto>> pendientes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarPendientes(principal));
    }

    @GetMapping("/rfqs-abiertas")
    public ApiResponse<List<RfqDto>> rfqsAbiertas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarRfqsAbiertas(principal));
    }

    @GetMapping("/proveedores-habituales")
    public ApiResponse<List<ProveedorHabitualDto>> proveedoresHabituales(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarProveedoresHabituales(principal));
    }

    @PutMapping("/productos/{productId}/proveedor-habitual")
    public ApiResponse<ProveedorHabitualDto> asignarProveedorHabitual(
            @PathVariable UUID productId,
            @RequestBody AsignarProveedorHabitualRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.asignarProveedorHabitual(productId, request.supplierId(), principal),
                "Proveedor habitual actualizado.");
    }

    @GetMapping("/requerimientos/{id}")
    public ApiResponse<ComprasDetalleDto> detalleCompra(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtenerDetalleCompra(id, principal));
    }

    @PostMapping("/requerimientos/{id}/rfq")
    public ApiResponse<RfqDto> crearRfq(
            @PathVariable UUID id,
            @Valid @RequestBody CrearRfqRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RfqDto creada = service.crearRfq(id, request, principal);
        return ApiResponse.ok(creada, "RFQ " + creada.rfqCode() + " creada.");
    }

    @GetMapping("/rfq/{id}")
    public ApiResponse<RfqDetalleDto> obtenerRfq(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtenerRfq(id, principal));
    }

    @PostMapping("/rfq/{id}/cerrar")
    public ApiResponse<RfqDto> cerrarRfq(
            @PathVariable UUID id,
            @RequestBody(required = false) MotivoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RfqDto rfq = service.cerrarRfq(id, request != null ? request.motivo() : null, principal);
        return ApiResponse.ok(rfq, "RFQ " + rfq.rfqCode() + " cerrada.");
    }

    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentoDto> subirDocumento(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tipo", defaultValue = "COTIZACION") String tipo,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.subirDocumento(file, tipo, principal), "Documento adjuntado.");
    }

    @PostMapping(value = "/rfq/{id}/cotizaciones/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrCotizacionDto> extraerOcr(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.extraerOcr(id, file, principal), "Datos extraidos, revisalos antes de guardar.");
    }

    @PostMapping("/rfq/{id}/cotizaciones")
    public ApiResponse<CotizacionDto> registrarCotizacion(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarCotizacionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        CotizacionDto cotizacion = service.registrarCotizacion(id, request, principal);
        return ApiResponse.ok(cotizacion, "Cotizacion registrada.");
    }

    @PostMapping("/cotizaciones/{id}/descartar")
    public ApiResponse<CotizacionDto> descartarCotizacion(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.descartarCotizacion(id, principal), "Cotizacion descartada.");
    }

    @PostMapping("/cotizaciones/{id}/seleccionar")
    public ApiResponse<OrdenDto> seleccionarCotizacion(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        OrdenDto orden = service.seleccionarCotizacion(id, principal);
        return ApiResponse.ok(orden, "Orden de compra " + orden.orderCode() + " emitida.");
    }

    @GetMapping("/ordenes")
    public ApiResponse<List<OrdenDto>> ordenes(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarOrdenes(principal));
    }

    @GetMapping("/ordenes/{id}")
    public ApiResponse<OrdenDetalleDto> obtenerOrden(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtenerOrden(id, principal));
    }

    @PostMapping("/ordenes/{id}/anular")
    public ApiResponse<OrdenDto> anularOrden(
            @PathVariable UUID id,
            @RequestBody(required = false) MotivoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        OrdenDto orden = service.anularOrden(id, request != null ? request.motivo() : null, principal);
        return ApiResponse.ok(orden, "Orden de compra " + orden.orderCode() + " anulada.");
    }

    @GetMapping("/monedas")
    public ApiResponse<List<MonedaDto>> monedas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarMonedas(principal));
    }
}
