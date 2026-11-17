package com.tdem.logistica.modules.requerimientos.dto;

import java.util.List;

/**
 * Tablero Kanban de Logistica (DEC-L067): los requerimientos en curso por etapa
 * del flujo, calculados con la misma consulta de la bandeja (DEC-L048).
 * {@code tarjetas} trae como maximo {@code TableroDto.MAX_POR_COLUMNA}; {@code total} es el conteo real.
 */
public record TableroDto(List<Columna> columnas, List<BandejaDto.Empresa> empresas) {

    public static final int MAX_POR_COLUMNA = 60;

    public record Columna(String id, String label, int total, List<BandejaDto.Fila> tarjetas) {
    }
}
