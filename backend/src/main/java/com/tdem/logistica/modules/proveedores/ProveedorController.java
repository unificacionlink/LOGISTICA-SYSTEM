package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.proveedores.dto.ComprasProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores;
import com.tdem.logistica.modules.proveedores.dto.DesempenoProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.DocumentosProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.EquivalenciaDto;
import com.tdem.logistica.modules.proveedores.dto.EvaluacionDto;
import com.tdem.logistica.modules.proveedores.dto.FichaProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.GuardarProveedorRequest;
import com.tdem.logistica.modules.proveedores.dto.PanelProveedoresDto;
import com.tdem.logistica.modules.proveedores.dto.PlantillaDto;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.RegistrarEvaluacionRequest;
import com.tdem.logistica.modules.proveedores.dto.SolicitudesProveedor;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Modulo de Proveedores (DEC-L069..L071). Toda la autorizacion vive en los servicios. */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService service;
    private final EquivalenciaService equivalencias;
    private final FichaProveedorService fichas;
    private final CicloProveedorService ciclo;
    private final EvaluacionProveedorService evaluaciones;
    private final PlantillaEvaluacionService plantillas;
    private final DocumentoProveedorService documentos;
    private final HistorialComercialService historial;

    public ProveedorController(ProveedorService service, EquivalenciaService equivalencias,
                               FichaProveedorService fichas, CicloProveedorService ciclo,
                               EvaluacionProveedorService evaluaciones, PlantillaEvaluacionService plantillas,
                               DocumentoProveedorService documentos, HistorialComercialService historial) {
        this.service = service;
        this.equivalencias = equivalencias;
        this.fichas = fichas;
        this.ciclo = ciclo;
        this.evaluaciones = evaluaciones;
        this.plantillas = plantillas;
        this.documentos = documentos;
        this.historial = historial;
    }

    /** Como llama este proveedor a nuestros productos (DEC-L053). */
    @GetMapping("/{id}/equivalencias")
    public ApiResponse<List<EquivalenciaDto>> equivalencias(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(equivalencias.listar(id, principal));
    }

    @DeleteMapping("/equivalencias/{id}")
    public ApiResponse<Void> quitarEquivalencia(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        equivalencias.quitar(id, principal);
        return ApiResponse.ok(null, "Equivalencia quitada.");
    }

    /** Proveedores activos para Compras (contrato de siempre). */
    @GetMapping
    public ApiResponse<List<ProveedorDto>> listar(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(principal));
    }

    /** Pantalla de Proveedores: todos los estados e indicadores. */
    @GetMapping("/gestion")
    public ApiResponse<PanelProveedoresDto> gestion(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(fichas.panel(principal));
    }

    @GetMapping("/productos-disponibles")
    public ApiResponse<List<SolicitudesProveedor.ProductoDisponible>> productosDisponibles(
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.productosDisponibles(principal));
    }

    @GetMapping("/configuracion")
    public ApiResponse<ConfiguracionProveedores.Datos> configuracion(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(plantillas.configuracion(principal));
    }

    @PutMapping("/configuracion/plantillas/{kind}")
    public ApiResponse<PlantillaDto> guardarPlantilla(
            @PathVariable String kind, @Valid @RequestBody ConfiguracionProveedores.GuardarPlantilla request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(plantillas.guardarPlantilla(kind, request, principal), "Plantilla guardada.");
    }

    @PutMapping("/configuracion/requisitos")
    public ApiResponse<List<ConfiguracionProveedores.Requisito>> guardarRequisitos(
            @Valid @RequestBody ConfiguracionProveedores.GuardarRequisitos request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(plantillas.guardarRequisitos(request.requisitos(), principal), "Requisitos guardados.");
    }

    @GetMapping("/{id}")
    public ApiResponse<ProveedorDto> obtener(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    @GetMapping("/{id}/ficha")
    public ApiResponse<FichaProveedorDto> ficha(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(fichas.ficha(id, principal));
    }

    @PostMapping
    public ApiResponse<ProveedorDto> crear(
            @Valid @RequestBody GuardarProveedorRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto creado = service.crear(request, principal);
        return ApiResponse.ok(creado, "Proveedor " + creado.legalName() + " registrado como borrador.");
    }

    @PutMapping("/{id}")
    public ApiResponse<ProveedorDto> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody GuardarProveedorRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto actualizado = service.actualizar(id, request, principal);
        return ApiResponse.ok(actualizado, "Proveedor " + actualizado.legalName() + " actualizado.");
    }

    @PutMapping("/{id}/categorias")
    public ApiResponse<FichaProveedorDto> categorias(
            @PathVariable UUID id, @Valid @RequestBody SolicitudesProveedor.Categorias request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        service.guardarCategorias(id, request.items(), principal);
        return ApiResponse.ok(fichas.ficha(id, principal), "Categorías guardadas.");
    }

    @PutMapping("/{id}/productos")
    public ApiResponse<FichaProveedorDto> productos(
            @PathVariable UUID id, @Valid @RequestBody SolicitudesProveedor.Productos request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        service.guardarProductos(id, request.productIds(), principal);
        return ApiResponse.ok(fichas.ficha(id, principal), "Productos guardados.");
    }

    @PostMapping("/{id}/enviar")
    public ApiResponse<FichaProveedorDto> enviar(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        ciclo.enviar(id, principal);
        return ApiResponse.ok(fichas.ficha(id, principal), "Enviado a revisión.");
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<FichaProveedorDto> decidir(
            @PathVariable UUID id, @Valid @RequestBody SolicitudesProveedor.Decision request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto p = ciclo.decidir(id, request.decision(), request.motivo(), principal);
        String mensaje = switch (p.status()) {
            case "HABILITADO" -> "Proveedor habilitado: ya aparece en Compras.";
            case "OBSERVADO" -> "Solicitud observada.";
            default -> "Solicitud rechazada.";
        };
        return ApiResponse.ok(fichas.ficha(id, principal), mensaje);
    }

    @PostMapping("/{id}/reabrir")
    public ApiResponse<FichaProveedorDto> reabrir(
            @PathVariable UUID id, @Valid @RequestBody SolicitudesProveedor.Motivo request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ciclo.reabrir(id, request.motivo(), principal);
        return ApiResponse.ok(fichas.ficha(id, principal), "Solicitud reabierta como borrador.");
    }

    @PostMapping("/{id}/estado-activo")
    public ApiResponse<FichaProveedorDto> estadoActivo(
            @PathVariable UUID id, @Valid @RequestBody SolicitudesProveedor.EstadoActivo request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ProveedorDto p = ciclo.cambiarActivo(id, request.activo(), request.motivo(), principal);
        return ApiResponse.ok(fichas.ficha(id, principal), p.active() ? "Proveedor activado." : "Proveedor desactivado.");
    }

    @GetMapping("/{id}/historial")
    public ApiResponse<List<SolicitudesProveedor.Historial>> historialDecisiones(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(fichas.historial(id, principal));
    }

    @GetMapping("/{id}/evaluaciones")
    public ApiResponse<List<EvaluacionDto>> evaluaciones(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(evaluaciones.listar(id, principal));
    }

    @PostMapping("/{id}/evaluaciones")
    public ApiResponse<EvaluacionDto> registrarEvaluacion(
            @PathVariable UUID id, @Valid @RequestBody RegistrarEvaluacionRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        EvaluacionDto e = evaluaciones.registrar(id, request, principal);
        return ApiResponse.ok(e, ("SELECCION".equals(e.kind()) ? "Selección" : "Evaluación") + " registrada con nota "
                + e.finalScore().stripTrailingZeros().toPlainString() + ".");
    }

    @PostMapping("/evaluaciones/{evaluationId}/anular")
    public ApiResponse<EvaluacionDto> anularEvaluacion(
            @PathVariable UUID evaluationId, @Valid @RequestBody SolicitudesProveedor.Motivo request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(evaluaciones.anular(evaluationId, request.motivo(), principal), "Evaluación anulada.");
    }

    @GetMapping("/{id}/documentos")
    public ApiResponse<DocumentosProveedorDto> documentos(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(documentos.listar(id, principal));
    }

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentosProveedorDto> subirDocumento(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "requirementId", required = false) UUID requirementId,
            @RequestParam(value = "evaluationId", required = false) UUID evaluationId,
            @RequestParam(value = "issuedAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
            @RequestParam(value = "expiresAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(documentos.subir(id, file, requirementId, evaluationId, issuedAt, expiresAt,
                notes != null && notes.length() > 500 ? notes.substring(0, 500) : notes, principal), "Documento guardado.");
    }

    @GetMapping("/documentos/{documentId}/archivo")
    public ResponseEntity<byte[]> descargarDocumento(
            @PathVariable UUID documentId, @AuthenticationPrincipal UsuarioPrincipal principal) {
        DocumentoProveedorService.Archivo a = documentos.descargar(documentId, principal);
        return Descargas.archivo(a.contenido(), a.nombre(), MediaType.parseMediaType(a.tipo()));
    }

    @PostMapping("/documentos/{documentId}/anular")
    public ApiResponse<DocumentosProveedorDto> anularDocumento(
            @PathVariable UUID documentId, @Valid @RequestBody SolicitudesProveedor.Motivo request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(documentos.anular(documentId, request.motivo(), principal), "Documento anulado.");
    }

    @GetMapping("/{id}/compras")
    public ApiResponse<ComprasProveedorDto> compras(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(historial.compras(id, principal));
    }

    @GetMapping("/{id}/desempeno")
    public ApiResponse<DesempenoProveedorDto> desempeno(
            @PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(historial.desempeno(id, principal));
    }
}
