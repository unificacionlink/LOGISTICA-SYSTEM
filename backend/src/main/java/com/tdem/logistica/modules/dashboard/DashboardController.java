package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Dashboard del Inicio: pestanas Proceso (DEC-L020), Demanda y Gasto (DEC-L083). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService service;
    private final DemandaService demandaService;
    private final GastoService gastoService;

    public DashboardController(DashboardService service, DemandaService demandaService, GastoService gastoService) {
        this.service = service;
        this.demandaService = demandaService;
        this.gastoService = gastoService;
    }

    @GetMapping
    public ApiResponse<DashboardDto> obtener(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) String empresa,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(dias, empresa, principal));
    }

    @GetMapping("/demanda")
    public ApiResponse<DemandaDto> demanda(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String familia,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(demandaService.obtener(dias, empresa, familia, principal));
    }

    /** Todas las lineas de la demanda del periodo (la pantalla muestra las ultimas 100). */
    @GetMapping("/demanda/excel")
    public ResponseEntity<byte[]> demandaExcel(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String familia,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        byte[] xlsx = DemandaExcel.generar(demandaService.lineas(dias, empresa, familia, principal));
        String nombre = "DEMANDA_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(xlsx);
    }

    @GetMapping("/gasto")
    public ApiResponse<GastoDto> gasto(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) String empresa,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(gastoService.obtener(dias, empresa, principal));
    }
}
