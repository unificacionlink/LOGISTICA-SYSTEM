package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.presupuestos.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.documentos.Descargas;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Presupuestos semestrales por CECO (Lote 14, DEC-L036; PRE-01..PRE-04 de la V38.7). */
@RestController
@RequestMapping("/api/presupuestos")
public class PresupuestoController {

    private final PresupuestoService service;

    public PresupuestoController(PresupuestoService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- periodos

    @GetMapping("/periodos")
    public ApiResponse<PeriodosDto> periodos(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listarPeriodos(principal));
    }

    @PostMapping("/periodos")
    public ApiResponse<PeriodoDto> crearPeriodo(@Valid @RequestBody CrearPeriodoRequest request,
                                                @AuthenticationPrincipal UsuarioPrincipal principal) {
        PeriodoDto p = service.crearPeriodo(request, principal);
        return ApiResponse.ok(p, "Periodo " + p.anio() + "-S" + p.semestre() + " habilitado para " + p.cecoCode() + ".");
    }

    @PutMapping("/periodos/{id}/cierre")
    public ApiResponse<PeriodoDto> cerrarPeriodo(@PathVariable UUID id, @Valid @RequestBody CerrarPeriodoRequest request,
                                                 @AuthenticationPrincipal UsuarioPrincipal principal) {
        PeriodoDto p = service.cerrarPeriodo(id, request.cerrado(), principal);
        return ApiResponse.ok(p, p.cerrado() ? "Periodo cerrado: queda solo de consulta." : "Periodo reabierto.");
    }

    @PutMapping("/periodos/{id}/control")
    public ApiResponse<PeriodoDto> controlPeriodo(@PathVariable UUID id, @Valid @RequestBody ControlPeriodoRequest request,
                                                  @AuthenticationPrincipal UsuarioPrincipal principal) {
        PeriodoDto p = service.controlPeriodo(id, request.obligatorio(), principal);
        return ApiResponse.ok(p, p.controlObligatorio()
                ? "Control presupuestal activado: los requerimientos del periodo se validan al enviarse."
                : "Control presupuestal desactivado.");
    }

    // ---------------------------------------------------------------- presupuestos

    @GetMapping
    public ApiResponse<List<PresupuestoResumenDto>> listar(@RequestParam(required = false) Integer anio,
                                                           @RequestParam(required = false) Integer semestre,
                                                           @RequestParam(required = false) String estado,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(anio, semestre, estado, principal));
    }

    @PostMapping
    public ApiResponse<PresupuestoDetalleDto> crear(@Valid @RequestBody CrearPresupuestoRequest request,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        PresupuestoDetalleDto p = service.crear(request, principal);
        return ApiResponse.ok(p, "Presupuesto " + p.codigo() + " creado en borrador.");
    }

    @GetMapping("/{id}")
    public ApiResponse<PresupuestoDetalleDto> obtener(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    @GetMapping("/{id}/catalogo")
    public ApiResponse<List<ProductoPresupuestoDto>> catalogo(@PathVariable UUID id,
                                                              @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.catalogo(id, principal));
    }

    @GetMapping("/{id}/cumplimiento")
    public ApiResponse<CumplimientoDto> cumplimiento(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.cumplimiento(id, principal));
    }

    /** RES-02: Excel de cumplimiento, exclusivo de PYC. */
    @GetMapping("/{id}/cumplimiento/excel")
    public ResponseEntity<byte[]> cumplimientoExcel(@PathVariable UUID id,
                                                    @AuthenticationPrincipal UsuarioPrincipal principal) {
        PresupuestoService.Archivo excel = service.cumplimientoExcel(id, principal);
        return Descargas.archivo(excel.contenido(), excel.nombre(), Descargas.XLSX);
    }

    /** Programacion del presupuesto en Excel. */
    @GetMapping("/{id}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        PresupuestoService.Archivo excel = service.excel(id, principal);
        return Descargas.archivo(excel.contenido(), excel.nombre(), Descargas.XLSX);
    }

    @PutMapping("/{id}/lineas")
    public ApiResponse<PresupuestoDetalleDto> guardarLineas(@PathVariable UUID id,
                                                            @Valid @RequestBody GuardarLineasRequest request,
                                                            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.guardarLineas(id, request.lineas(), principal), "Programacion guardada.");
    }

    @PostMapping("/{id}/emitir")
    public ApiResponse<PresupuestoDetalleDto> emitir(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        PresupuestoDetalleDto p = service.emitir(id, principal);
        return ApiResponse.ok(p, "Presupuesto emitido a " + p.aprobador() + ".");
    }

    @PostMapping("/{id}/decidir")
    public ApiResponse<PresupuestoDetalleDto> decidir(@PathVariable UUID id,
                                                      @Valid @RequestBody DecidirPresupuestoRequest request,
                                                      @AuthenticationPrincipal UsuarioPrincipal principal) {
        PresupuestoDetalleDto p = service.decidir(id, request.decision(), request.motivo(), principal);
        if (!"APROBADO".equals(p.estado())) {
            return ApiResponse.ok(p, "Presupuesto observado.");
        }
        long borradores = p.requerimientos().stream().filter(r -> "BORRADOR".equals(r.estado())).count();
        return ApiResponse.ok(p, "Presupuesto aprobado: version " + p.versionVigente() + " con precios congelados."
                + (borradores == 0 ? "" : borradores == 1 ? " Se dejo 1 requerimiento en borrador."
                : " Se dejaron " + borradores + " requerimientos en borrador."));
    }

    @PostMapping("/{id}/modificar")
    public ApiResponse<PresupuestoDetalleDto> modificar(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.modificar(id, principal),
                "Presupuesto en modificacion: sigue rigiendo la version aprobada hasta la nueva aprobacion.");
    }

    @PostMapping("/{id}/anular")
    public ApiResponse<PresupuestoDetalleDto> anular(@PathVariable UUID id, @Valid @RequestBody MotivoRequest request,
                                                     @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.anular(id, request.motivo(), principal), "Presupuesto anulado.");
    }
}
