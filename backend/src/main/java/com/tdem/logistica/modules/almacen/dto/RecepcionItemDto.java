package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecepcionItemDto(
        UUID orderItemId,
        String description,
        BigDecimal qtyReceived,
        String conditionStatus,
        String observation) {
}
