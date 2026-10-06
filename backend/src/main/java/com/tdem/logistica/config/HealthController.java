package com.tdem.logistica.config;

import com.tdem.logistica.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "app", "ERP Logistico Corporativo",
                "module", "Requerimientos y Compras"
        ), "Servicio operativo.");
    }
}
