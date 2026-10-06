package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record IncidenciaInput(
        @NotBlank(message = "Indica el tipo de incidencia.") String issueType,
        @DecimalMin(value = "0.001", message = "La cantidad afectada debe ser mayor a 0.") BigDecimal qtyAffected,
        @NotBlank(message = "Describe la incidencia.") String description,
        String supplierAction) {
}
