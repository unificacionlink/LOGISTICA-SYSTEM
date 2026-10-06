package com.tdem.logistica.modules.requerimientos.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RequerimientoDto(
        UUID id,
        String code,
        UUID requesterUserId,
        UUID cecoId,
        String cecoNombre,
        String requesterNombre,
        String priority,
        String purpose,
        String justification,
        String status,
        LocalDate requiredDate,
        LocalDate scheduledDeliveryDate,
        OffsetDateTime createdAt,
        String empresaCode) {
}
