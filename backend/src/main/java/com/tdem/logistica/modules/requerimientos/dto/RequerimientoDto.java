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
        String empresaCode,
        /** Solo en el listado: cantidad de items y nombres de sus productos (para buscar y resumir). */
        Integer itemsCount,
        String resumenItems,
        /** DEC-L054: el solicitante sugiere la caja chica del proyecto. */
        boolean cajaChicaSugerida,
        String cajaChicaNota) {

    public RequerimientoDto conResumen(Integer items, String resumen) {
        return new RequerimientoDto(id, code, requesterUserId, cecoId, cecoNombre, requesterNombre, priority, purpose,
                justification, status, requiredDate, scheduledDeliveryDate, createdAt, empresaCode, items, resumen,
                cajaChicaSugerida, cajaChicaNota);
    }
}
