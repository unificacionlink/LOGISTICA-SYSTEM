package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Condiciones de la OC que Logistica ajusta mientras espera aprobacion
 * (formato F-LOG-09, DEC-L079). {@code taxMode}: MAS_IGV, IGV_INCLUIDO o NO_AFECTO.
 */
public record CondicionesOrdenRequest(
        @NotBlank(message = "Indica como se aplica el IGV.") String taxMode,
        /** DEC-L089: plazo en dias y si son HABILES o CALENDARIO; lugar de la lista (null = sin lugar). */
        @jakarta.validation.constraints.NotNull(message = "Indica el plazo de entrega en días.") Integer deliveryDays,
        @NotBlank(message = "Indica si el plazo es en días hábiles o calendario.") String deliveryDayType,
        java.util.UUID deliveryPointId,
        @Size(max = 2000, message = "Las especificaciones admiten hasta 2000 caracteres.") String additionalSpecs,
        /** Entrega directa del proveedor al CECO (DEC-L084); null = no cambia. */
        Boolean entregaDirecta) {
}
