package com.tdem.logistica.modules.almacen.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecepcionDto(
        UUID receiptId,
        Integer receiptNumber,
        Instant receiptDate,
        String status,
        String receivedByName,
        String observation,
        String guia,
        List<RecepcionItemDto> items) {
}
