package com.tdem.logistica.modules.almacen.dto;

import java.util.List;

/**
 * RF-031: resultado de validar (o aplicar) un Excel de ingresos. Solo lista
 * las filas con cantidad a ingresar; las demas se cuentan como omitidas.
 */
public record CargaMasivaDto(
        String archivo,
        int omitidas,
        int validas,
        int conError,
        int ordenes,
        boolean aplicada,
        List<CargaMasivaFilaDto> filas) {
}
