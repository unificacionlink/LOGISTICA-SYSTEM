package com.tdem.logistica.modules.dashboard;

import com.tdem.logistica.common.ApiResponse;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DashboardDto> obtener(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) String empresa,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ApiResponse.ok(service.obtener(dias, empresa, principal));
    }
}
