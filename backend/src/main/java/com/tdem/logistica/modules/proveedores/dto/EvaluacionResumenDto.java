package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EvaluacionResumenDto(
        UUID evaluationId,
        String kind,
        String templateCode,
        LocalDate evaluatedAt,
        BigDecimal finalScore,
        BigDecimal scaleMax,
        BigDecimal threshold,
        String result,
        LocalDate nextEvaluationDate) {
}
