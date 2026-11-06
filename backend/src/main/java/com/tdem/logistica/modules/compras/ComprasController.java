package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.common.MotivoRequest;
import com.tdem.logistica.modules.compras.dto.*;
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
    private final AbastecimientoService abastecimiento;
    private final ComparativoService comparativo;

    public ComprasController(ComprasService service, AbastecimientoService abastecimiento,
                             ComparativoService comparativo) {
        this.service = service;
        this.abastecimiento = abastecimiento;
        this.comparativo = comparativo;
    }

    // ---------------------------------------------------------------- Comparativo (DEC-L047)

    @GetMapping("/comparativo")
    public ApiResponse<ComparativoDto> comparativo(
            @RequestParam("ids") List<UUID> ids, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(comparativo.obtener(ids, principal));
    }

    @PostMapping(value = "/comparativo/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OcrComparativoDto> leerCotizacion(
            @RequestParam("ids") List<UUID> ids,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        OcrComparativoDto ocr = comparativo.leer(ids, file, principal);
        return ApiResponse.ok(ocr, ocr.precios().isEmpty()
                ? "No se reconocieron precios: ingresalos a mano."
                : ocr.precios().size() + " precio(s) leido(s): revisalos antes de guardar.");
    }

    @PostMapping("/comparativo/cotizaciones")
    public ApiResponse<ComparativoDto> registrarEnComparativo(
            @Valid @RequestBody RegistrarComparativoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(comparativo.registrar(request, principal), "Cotizacion guardada.");
    }

    @PostMapping("/comparativo/adjudicar")
    public ApiResponse<ComparativoDto> adjudicarComparativo(
            @Valid @RequestBody AdjudicarComparativoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(comparativo.adjudicar(request, principal), "Productos adjudicados.");
    }

    // ---------------------------------------------------------------- Lote 16 (DEC-L039)

    @GetMapping("/necesidades")
    public ApiResponse<List<NecesidadDto>> necesidades(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.listarNecesidades(principal));
    }

    @PostMapping("/necesidades/{id}/ruta")
    public ApiResponse<NecesidadDto> decidirRuta(
            @PathVariable UUID id,
            @Valid @RequestBody DecidirRutaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        NecesidadDto n = abastecimiento.decidirRuta(id, request.ruta(), principal);
        return ApiResponse.ok(n, "COMPRA".equals(n.status())
                ? "Agregado al consolidado " + n.consolidationCode() + "."
                : "Ruta Caja Chica confirmada.");
    }

    @GetMapping("/consolidados")
    public ApiResponse<List<ConsolidadoDto>> consolidados(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.listarConsolidados(principal));
    }

    @GetMapping("/consolidados/{id}")
    public ApiResponse<ConsolidadoDetalleDto> consolidado(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.obtenerConsolidado(id, principal));
    }

    @PostMapping("/consolidados/solicitudes")
    public ApiResponse<List<RfqDto>> prepararSolicitudes(
            @Valid @RequestBody PrepararSolicitudesRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        List<RfqDto> rfqs = abastecimiento.prepararSolicitudes(request.consolidationIds(), principal);
        return ApiResponse.ok(rfqs, rfqs.size() == 1 ? "1 solicitud de cotizacion preparada."
                : rfqs.size() + " solicitudes de cotizacion preparadas.");
    }

    @PostMapping("/consolidados/{id}/adjudicar")
    public ApiResponse<ConsolidadoDetalleDto> adjudicar(
            @PathVariable UUID id,
            @Valid @RequestBody AdjudicarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.adjudicar(id, request.quotationItemId(), principal), "Producto adjudicado.");
    }

    @PostMapping("/consolidados/{id}/anular")
    public ApiResponse<ConsolidadoDetalleDto> anularConsolidado(
            @PathVariable UUID id,
            @RequestBody(required = false) MotivoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.anularConsolidado(id, request != null ? request.motivo() : null, principal),
                "Consolidado anulado: sus faltantes vuelven a decidir ruta.");
    }

    @GetMapping("/por-emitir")
    public ApiResponse<List<PorEmitirDto>> porEmitir(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.listarPorEmitir(principal));
    }

    @PostMapping("/cotizaciones/{id}/emitir-oc")
    public ApiResponse<OrdenDto> emitirOrden(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        OrdenDto orden = abastecimiento.emitirOrden(id, principal);
        return ApiResponse.ok(orden, "Orden de compra " + orden.orderCode() + " emitida.");
    }

    @PostMapping("/rfq/{id}/enviar")
    public ApiResponse<RfqDto> confirmarEnvio(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmarEnvioRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        RfqDto rfq = abastecimiento.confirmarEnvio(id, request.canal(), principal);
        return ApiResponse.ok(rfq, "Envio de " + rfq.rfqCode() + " confirmado.");
    }

    @GetMapping("/proveedores-producto")
    public ApiResponse<List<ProveedorProductoDto>> cobertura(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.listarCobertura(principal));
    }

    @PutMapping("/productos/{productId}/proveedores")
    public ApiResponse<List<ProveedorProductoDto>> reemplazarCobertura(
            @PathVariable UUID productId,
            @RequestBody ProveedoresProductoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(abastecimiento.reemplazarCobertura(productId, request.supplierIds(), principal),
                "Proveedores del producto actualizados.");
    }

    // ---------------------------------------------------------------- RFQ, cotizaciones y OC

    @GetMapping("/rfqs-abiertas")
    public ApiResponse<List<RfqDto>> rfqsAbiertas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarRfqsAbiertas(principal));
    }

    @GetMapping("/requerimientos/{id}")
    public ApiResponse<ComprasDetalleDto> detalleCompra(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtenerDetalleCompra(id, principal));
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
