package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Una ficha de seleccion (F-LOG-03) o evaluacion (F-LOG-07) con sus criterios tal como se registraron. */
public record EvaluacionDto(
        UUID evaluationId,
        String kind,
        String templateCode,
        int templateVersion,
        LocalDate evaluatedAt,
        String evaluador,
        String area,
        String scope,
        String userAreaReviewer,
        String logisticsConformity,
        String userAreaConformity,
        BigDecimal finalScore,
        BigDecimal scaleMax,
        BigDecimal threshold,
        String result,
        String observations,
        LocalDate nextEvaluationDate,
        String status,
        String voidReason,
        OffsetDateTime voidedAt,
        List<Criterio> criterios) {

    public record Criterio(int sortOrder, String criterionName, BigDecimal weight, BigDecimal score,
                           String scoreLabel, String observation) {
    }
}
