package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Plantilla vigente de seleccion o de evaluacion (DEC-L070). */
public record PlantillaDto(
        UUID templateId,
        String kind,
        String code,
        int version,
        String name,
        BigDecimal scaleMax,
        BigDecimal approvalThreshold,
        int validityMonths,
        int alertDays,
        List<Criterio> criterios) {

    public record Criterio(UUID criterionId, int sortOrder, String name, BigDecimal weight, List<Opcion> opciones) {
    }

    public record Opcion(BigDecimal score, String label) {
    }
}
