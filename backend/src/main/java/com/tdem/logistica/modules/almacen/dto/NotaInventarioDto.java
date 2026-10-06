package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record NotaInventarioDto(
        UUID inventoryNoteId,
        String inventoryNoteCode,
        UUID requestId,
        String requestCode,
        String empresaCode,
        String cecoNombre,
        String movementType,
        LocalDate pickingDate,
        String pickingUserName,
        String itemState,
        BigDecimal totalItemQty,
        BigDecimal totalValueSnapshot,
        Integer packageCount,
        String comments,
        String status,
        Instant issuedAt,
        EnvioDto envio,
        String almacenOrigen,
        String cecoCodigo,
        String solicitante) {
}
