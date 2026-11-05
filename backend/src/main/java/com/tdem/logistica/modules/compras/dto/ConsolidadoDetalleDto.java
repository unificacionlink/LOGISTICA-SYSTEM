package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ConsolidadoDetalleDto(
        ConsolidadoDto consolidado,
        List<OfertaDto> ofertas,
        BigDecimal ultimoCosto,
        UUID orderId,
        String orderCode) {
}
