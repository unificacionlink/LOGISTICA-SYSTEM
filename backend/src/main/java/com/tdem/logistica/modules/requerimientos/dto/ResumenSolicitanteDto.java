package com.tdem.logistica.modules.requerimientos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de todo lo que pidio el usuario (DEC-L076). Las unidades cuentan
 * solo lo enviado (sin borradores ni rechazados); entregado = aceptado en
 * destino mas lo regularizado por caja chica del proyecto.
 */
public record ResumenSolicitanteDto(
        int total,
        Map<String, Integer> estados,
        BigDecimal unidadesPedidas,
        BigDecimal unidadesEntregadas,
        List<Producto> productos,
        List<Reciente> recientes) {

    public record Producto(String sku, String nombre, int veces, BigDecimal pedidas, BigDecimal entregadas) {
    }

    public record Reciente(UUID id, String codigo, String estado, Instant creado, int items, String resumen,
                           double avance) {
    }
}
