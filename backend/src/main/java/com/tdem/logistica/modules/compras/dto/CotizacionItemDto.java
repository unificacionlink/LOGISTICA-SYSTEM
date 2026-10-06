package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CotizacionItemDto(
        UUID quotationItemId,
        UUID rfqItemId,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        String offeredBrand,
        String offeredModel) {
}
