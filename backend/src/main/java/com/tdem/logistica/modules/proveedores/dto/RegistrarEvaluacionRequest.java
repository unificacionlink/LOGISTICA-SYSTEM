package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrarEvaluacionRequest(
        @NotNull @Pattern(regexp = "SELECCION|DESEMPENO", message = "Tipo de evaluacion invalido.") String kind,
        @NotNull(message = "Indica la fecha de evaluacion.") LocalDate evaluatedAt,
        @Size(max = 120) String area,
        @Size(max = 500) String scope,
        @Size(max = 150) String userAreaReviewer,
        @NotNull(message = "Indica la conformidad de Logistica.")
        @Pattern(regexp = "SI|NO") String logisticsConformity,
        @NotNull(message = "Indica la conformidad del area usuaria.")
        @Pattern(regexp = "SI|NO") String userAreaConformity,
        @Size(max = 2000) String observations,
        /** Opcional: si no llega, fecha de evaluacion + meses de vigencia de la plantilla. */
        LocalDate nextEvaluationDate,
        @NotEmpty(message = "Califica los criterios.") List<@Valid Puntaje> criterios) {

    public record Puntaje(
            @NotNull UUID criterionId,
            @NotNull(message = "Falta el puntaje de un criterio.") @Digits(integer = 3, fraction = 2) BigDecimal score,
            @Size(max = 1000) String observation) {
    }
}
