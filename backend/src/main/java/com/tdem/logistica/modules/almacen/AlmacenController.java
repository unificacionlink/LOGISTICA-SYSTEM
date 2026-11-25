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
    private final PreparacionNotaService preparacionService;
    private final RecepcionDestinoService destinoService;
    private final DiferenciasDestinoService diferenciasService;
    private final EntregaDirectaService entregaDirectaService;

    public AlmacenController(RecepcionService recepcionService, DespachoService despachoService,
                             CargaMasivaService cargaMasivaService, PreparacionNotaService preparacionService,
                             RecepcionDestinoService destinoService, DiferenciasDestinoService diferenciasService,
                             EntregaDirectaService entregaDirectaService) {
        this.recepcionService = recepcionService;
        this.despachoService = despachoService;
        this.cargaMasivaService = cargaMasivaService;
        this.preparacionService = preparacionService;
        this.destinoService = destinoService;
        this.diferenciasService = diferenciasService;
        this.entregaDirectaService = entregaDirectaService;
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

    // ---------- Entrega directa del proveedor al CECO (ALM-04, DEC-L084) ----------

    @GetMapping("/ordenes/{id}/entrega-directa")
    public ApiResponse<EntregaDirectaDto> entregaDirecta(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(entregaDirectaService.obtener(id, principal));
    }

    @PostMapping(value = "/ordenes/{id}/entrega-directa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EntregaDirectaDto> registrarEntregaDirecta(
            @PathVariable UUID id,
            @Valid @RequestPart("datos") RegistrarEntregaDirectaRequest datos,
            @RequestPart(value = "file", required = false) MultipartFile constancia,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(entregaDirectaService.registrar(id, datos, constancia, principal), "Recepcion registrada.");
    }

    /** Lo que el proveedor entrega directo para este requerimiento (dueno, Logistica, aprobador, Gerencia). */
    @GetMapping("/requerimientos/{id}/entregas-directas")
    public ApiResponse<List<EntregaDirectaDto>> entregasDirectasDelRequerimiento(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(entregaDirectaService.delRequerimiento(id, principal));
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

    /** DEC-L088: anula una nota sin envio; devuelve lo despachado al stock y a la reserva del REQ. */
    @PostMapping("/notas/{id}/anular")
    public ApiResponse<NotaInventarioDetalleDto> anularNota(
            @PathVariable UUID id,
            @Valid @RequestBody AnularNotaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(despachoService.anularNota(id, request.motivo(), principal),
                "Nota anulada: lo despachado volvió al stock y sigue reservado para el requerimiento.");
    }

    // ---------- Preparacion (ALM-02) ----------

    @PostMapping("/notas/{id}/picking")
    public ApiResponse<NotaInventarioDetalleDto> confirmarPicking(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(preparacionService.confirmarPicking(id, principal), "Picking confirmado.");
    }

    @PostMapping("/notas/{id}/embalaje")
    public ApiResponse<NotaInventarioDetalleDto> embalar(
            @PathVariable UUID id,
            @Valid @RequestBody EmbalarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(preparacionService.embalar(id, request.bultos(), request.evidencia(), principal),
                "Embalaje registrado.");
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

    // ---------- Recepcion en destino y diferencias (ALM-03) ----------

    @PostMapping(value = "/envios/{id}/recepcion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<NotaInventarioDetalleDto> recibirEnDestino(
            @PathVariable UUID id,
            @Valid @RequestPart("datos") RecepcionDestinoRequest datos,
            @RequestPart(value = "file", required = false) MultipartFile notaFirmada,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(destinoService.registrar(id, datos, notaFirmada, principal), "Recepcion registrada.");
    }

    @PostMapping("/envios/{id}/resolucion")
    public ApiResponse<NotaInventarioDetalleDto> resolverObservado(
            @PathVariable UUID id,
            @Valid @RequestBody ResolverObservadoRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(destinoService.resolver(id, request, principal), "Resolucion registrada.");
    }

    @GetMapping("/diferencias")
    public ApiResponse<DestinoDtos.DiferenciasDto> diferencias(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(diferenciasService.listar(principal));
    }

    @PostMapping("/devoluciones/{id}/recibir")
    public ApiResponse<DestinoDtos.DiferenciasDto> recibirDevolucion(
            @PathVariable UUID id,
            @Valid @RequestBody EvidenciaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        destinoService.recibirDevolucion(id, request, principal);
        return ApiResponse.ok(diferenciasService.listar(principal), "Retorno recibido en cuarentena.");
    }
}
