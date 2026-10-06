package com.tdem.logistica.modules.tracking;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/requerimientos/{id}/tracking")
public class TrackingController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final TrackingService service;

    public TrackingController(TrackingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<TrackingDto> obtener(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(id, principal));
    }

    /** RF-081: mismo DTO que la pantalla, exportado a Excel. */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@PathVariable UUID id, @AuthenticationPrincipal UsuarioPrincipal principal) {
        TrackingDto t = service.obtener(id, principal);
        String nombre = "TRACKING_" + t.requerimiento().code() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(TrackingExcel.generar(t));
    }
}
