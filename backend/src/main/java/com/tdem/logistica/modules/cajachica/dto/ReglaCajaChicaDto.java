package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lineamiento vigente de Caja Chica (RF-026). {@code empresaCode} null = corporativa. */
public record ReglaCajaChicaDto(
        UUID id,
        String empresaCode,
        BigDecimal umbral,
        String moneda,
        boolean bajoMonto,
        boolean mismoDia,
        boolean fallaProveedor,
        boolean excepcion,
        Instant actualizadaEn) {
}
