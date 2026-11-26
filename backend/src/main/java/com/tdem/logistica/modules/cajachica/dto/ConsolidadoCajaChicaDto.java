package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consolidado de caja chica (DEC-L092): gasto en PEN por CECO, mes y solicitante,
 * con el detalle de cada compra. Solo cuenta lo REGISTRADO (las anuladas, aparte).
 */
public record ConsolidadoCajaChicaDto(
        BigDecimal total,
        long compras,
        long anuladas,
        List<String> meses,
        List<Monto> porCeco,
        List<Monto> porMes,
        List<Monto> porSolicitante,
        List<CompraCajaChicaDto> detalle) {

    /** clave = codigo del CECO, mes (AAAA-MM) o nombre del solicitante; detalle = nombre del CECO o empresa. */
    public record Monto(String clave, String detalle, BigDecimal monto, long compras) {
    }
}
