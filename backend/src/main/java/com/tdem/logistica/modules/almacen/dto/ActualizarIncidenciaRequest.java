package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarIncidenciaRequest(
        @NotBlank(message = "Indica el estado.") String status,
        String supplierAction) {
}
