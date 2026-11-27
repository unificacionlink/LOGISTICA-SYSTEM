package com.tdem.logistica.modules.reportes;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.documentos.Descargas;
import com.tdem.logistica.modules.reportes.dto.ReporteObservacionDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Reportes de observaciones desde la pantalla (DEC-L104). */
@RestController
public class ReporteObservacionController {

    private final ReporteObservacionService service;

    public ReporteObservacionController(ReporteObservacionService service) {
        this.service = service;
    }

    /** Cualquier usuario con sesion. Multipart: la captura es opcional. */
    @PostMapping(value = "/api/reportes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReporteObservacionDto.Creado> reportar(
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String pantalla,
            @RequestParam String ruta,
            @RequestParam(required = false) String zona,
            @RequestParam String descripcion,
            @RequestParam(required = false) String viewport,
            @RequestPart(required = false) MultipartFile captura,
            HttpServletRequest http,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReporteObservacionDto.Creado creado = service.reportar(
                new ReporteObservacionService.Nuevo(modulo, pantalla, ruta, zona, descripcion, viewport),
                captura, http.getHeader("User-Agent"), principal);
        return ApiResponse.ok(creado, "Reporte " + ReporteObservacionExcel.codigo(creado.numero()) + " enviado.");
    }

    @GetMapping("/api/reportes/mios")
    public ApiResponse<List<ReporteObservacionDto.Propio>> mios(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.mios(principal));
    }

    @GetMapping("/api/admin/reportes")
    public ApiResponse<ReporteObservacionDto.Pagina> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.listar(estado, q, pagina, principal));
    }

    @GetMapping("/api/admin/reportes/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return Descargas.archivo(service.excel(estado, q, principal), "observaciones.xlsx", Descargas.XLSX);
    }

    public record RevisarRequest(@NotBlank String estado, @Size(max = 1_000) String respuesta) {
    }

    @PutMapping("/api/admin/reportes/{id}")
    public ApiResponse<ReporteObservacionDto.Fila> revisar(@PathVariable UUID id,
                                                           @Valid @RequestBody RevisarRequest r,
                                                           @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.revisar(id, r.estado(), r.respuesta(), principal), "Reporte actualizado.");
    }

    @GetMapping("/api/admin/reportes/{id}/captura")
    public ResponseEntity<byte[]> captura(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        ReporteObservacionService.Imagen img = service.captura(id, principal);
        return Descargas.archivo(img.contenido(), "captura" + (img.tipo().endsWith("png") ? ".png" : ".jpg"),
                MediaType.parseMediaType(img.tipo()));
    }
}
