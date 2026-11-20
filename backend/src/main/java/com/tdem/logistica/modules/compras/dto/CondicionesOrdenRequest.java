package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Condiciones de la OC que Logistica ajusta mientras espera aprobacion
 * (formato F-LOG-09, DEC-L079). {@code taxMode}: MAS_IGV, IGV_INCLUIDO o NO_AFECTO.
 */
public record CondicionesOrdenRequest(
        @NotBlank(message = "Indica como se aplica el IGV.") String taxMode,
        @Size(max = 120, message = "El plazo de entrega admite hasta 120 caracteres.") String deliveryTerm,
        @Size(max = 220, message = "El lugar de entrega admite hasta 220 caracteres.") String deliveryLocation,
        @Size(max = 2000, message = "Las especificaciones admiten hasta 2000 caracteres.") String additionalSpecs) {
}
