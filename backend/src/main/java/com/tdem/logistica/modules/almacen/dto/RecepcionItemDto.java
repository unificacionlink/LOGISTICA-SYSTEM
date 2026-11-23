package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecepcionItemDto(
        UUID orderItemId,
        String description,
        BigDecimal qtyReceived,
        String conditionStatus,
        String observation,
        /** Entrega directa: requerimiento al que se entrego. */
        String requestCode) {
}
