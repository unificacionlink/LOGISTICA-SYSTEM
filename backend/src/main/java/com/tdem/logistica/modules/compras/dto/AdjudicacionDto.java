package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AdjudicacionDto(
        UUID quotationItemId,
        UUID quotationId,
        String proveedorNombre,
        String currencyCode,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal lineTotal) {
}
