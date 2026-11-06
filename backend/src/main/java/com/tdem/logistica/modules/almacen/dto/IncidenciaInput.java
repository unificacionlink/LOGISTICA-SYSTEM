package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record IncidenciaInput(
        @NotBlank(message = "Indica el tipo de incidencia.") String issueType,
        @DecimalMin(value = "1", message = "La cantidad afectada debe ser al menos 1.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.") BigDecimal qtyAffected,
        @NotBlank(message = "Describe la incidencia.") String description,
        String supplierAction) {
}
