package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sugerencia de Caja Chica para un REQ (RF-023..025, RF-051). {@code estimado}
 * es el pendiente valorizado al ultimo costo conocido; null si algun item no
 * tiene costo de referencia.
 */
public record EvaluacionCajaChicaDto(ReglaCajaChicaDto regla, BigDecimal estimado, List<Motivo> motivos) {

    public record Motivo(String codigo, String detalle) {
    }
}
