package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/almacen")
public class AlmacenController {

    private final RecepcionService recepcionService;
    private final DespachoService despachoService;
    private final CargaMasivaService cargaMasivaService;

    public AlmacenController(RecepcionService recepcionService, DespachoService despachoService,
                             CargaMasivaService cargaMasivaService) {
        this.recepcionService = recepcionService;
        this.despachoService = despachoService;
        this.cargaMasivaService = cargaMasivaService;
    }

    // ---------- Carga masiva de ingresos (RF-031) ----------

    @GetMapping("/carga-masiva/plantilla")
    public ResponseEntity<byte[]> plantillaCargaMasiva(@AuthenticationPrincipal UsuarioPrincipal principal) {
        String nombre = "PLANTILLA_INGRESO_ALMACEN_" + java.time.LocalDate.now() + ".xlsx";
        return Descargas.archivo(cargaMasivaService.plantilla(principal), nombre, Descargas.XLSX);
    }

    @PostMapping(value = "/carga-masiva/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CargaMasivaDto> validarCargaMasiva(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(cargaMasivaService.validar(file, principal));
    }

    @PostMapping(value = "/carga-masiva/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CargaMasivaDto> confirmarCargaMasiva(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal UsuarioPrincipal principal) {
        CargaMasivaDto r = cargaMasivaService.confirmar(file, principal);
        return ApiResponse.ok(r, "Se registraron " + r.validas() + " ingreso(s) en " + r.ordenes() + " orden(es).");
    }

    // ---------- Recepcion de OC ----------

    @GetMapping("/ordenes-por-recibir")
    public ApiResponse<List<OrdenDto>> ordenesPorRecibir(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(recepcionService.listarPorRecibir(principal));
    }

    @GetMapping("/ordenes/{id}/recepcion")
    public ApiResponse<RecepcionOrdenDto> recepcionOrden(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(recepcionService.obtenerRecepcionOrden(id, principal));
    }

    @PostMapping("/ordenes/{id}/recepciones")
    public ApiResponse<RecepcionOrdenDto> registrarRecepcion(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarRecepcionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(recepcionService.registrar(id, request, principal), "Recepcion registrada.");
    }

    @PatchMapping("/incidencias/{id}")
    public ApiResponse<IncidenciaDto> actualizarIncidencia(
            @PathVariable UUID id,
            @Valid @RequestBody ActualizarIncidenciaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(recepcionService.actualizarIncidencia(id, request, principal), "Incidencia actualizada.");
    }

    // ---------- Despacho / Nota de Inventario ----------

    @GetMapping("/requerimientos-por-despachar")
    public ApiResponse<List<RequerimientoDto>> porDespachar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.listarPorDespachar(principal));
    }

    @GetMapping("/requerimientos/{id}/despacho")
    public ApiResponse<DespachoRequerimientoDto> despacho(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.obtenerDespacho(id, principal));
    }

    @PostMapping("/requerimientos/{id}/notas")
    public ApiResponse<NotaInventarioDetalleDto> crearNota(
            @PathVariable UUID id,
            @Valid @RequestBody CrearNotaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        NotaInventarioDetalleDto nota = despachoService.crearNotaInventario(id, request, principal);
        return ApiResponse.ok(nota, "Nota de inventario " + nota.nota().inventoryNoteCode() + " emitida.");
    }

    @GetMapping("/notas")
    public ApiResponse<List<NotaInventarioDto>> notas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.listarNotas(principal));
    }

    @GetMapping("/notas/{id}")
    public ApiResponse<NotaInventarioDetalleDto> nota(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.obtenerNota(id, principal));
    }

    // ---------- Envio ----------

    @PostMapping("/notas/{id}/envio")
    public ApiResponse<NotaInventarioDetalleDto> registrarEnvio(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrarEnvioRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.registrarEnvio(id, request, principal), "Envio registrado.");
    }

    @PostMapping(value = "/envios/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentoAdjuntoDto> adjuntar(
            @PathVariable UUID id,
            @RequestParam("tipo") String tipo,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.adjuntarDocumento(id, tipo, file, principal), "Documento adjuntado.");
    }

    @PostMapping("/envios/{id}/confirmar-recepcion")
    public ApiResponse<NotaInventarioDetalleDto> confirmarRecepcion(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmarRecepcionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.confirmarRecepcion(id, request, principal),
                "Recepcion en destino confirmada.");
    }
}
