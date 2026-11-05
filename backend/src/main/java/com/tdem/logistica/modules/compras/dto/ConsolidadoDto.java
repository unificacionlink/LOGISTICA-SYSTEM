package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsolidadoDto(
        UUID consolidationId,
        String consolidationCode,
        String empresaCode,
        UUID productId,
        String skuBase,
        String codigoVariante,
        String nombreProducto,
        String atributo,
        String unidadMedida,
        BigDecimal totalQty,
        String status,
        List<OrigenDto> origenes,
        List<String> proveedores,
        int cotizaciones,
        AdjudicacionDto adjudicado,
        Instant createdAt) {
}
