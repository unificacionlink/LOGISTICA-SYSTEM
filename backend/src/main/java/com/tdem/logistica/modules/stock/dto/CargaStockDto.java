package com.tdem.logistica.modules.stock.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de validar o aplicar el Excel de inventario (DEC-L045). Solo se
 * listan las filas con cambio, aviso o error.
 */
public record CargaStockDto(
        String archivo,
        String codigo,
        int filas,
        int contadas,
        int ajustes,
        int minimos,
        int sinCambios,
        int conError,
        boolean aplicada,
        List<Fila> detalle) {

    /** diferencia = contado - fisico (null si la fila no trae conteo). */
    public record Fila(int fila, String codigoVariante, String producto, BigDecimal fisico, BigDecimal contado,
                       BigDecimal diferencia, BigDecimal minimoAnterior, BigDecimal minimoNuevo, String aviso,
                       String error) {
    }
}
