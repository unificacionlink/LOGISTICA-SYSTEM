package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Necesidad que aporta a un consolidado: de que REQ y CECO (proyecto) viene cada parte. */
public record OrigenDto(
        UUID needId,
        UUID requestId,
        String requestCode,
        String cecoCode,
        String cecoNombre,
        String priority,
        LocalDate requiredDate,
        BigDecimal qty) {
}
