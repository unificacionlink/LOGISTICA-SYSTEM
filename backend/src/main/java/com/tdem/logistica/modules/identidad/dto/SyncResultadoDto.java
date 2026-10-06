package com.tdem.logistica.modules.identidad.dto;

import java.time.Instant;
import java.util.List;

/** Resultado de una sincronizacion con RRHH-SYSTEM (DEC-L024). */
public record SyncResultadoDto(
        Instant ejecutadoEn,
        int recibidos,
        int creados,
        int actualizados,
        int desactivados,
        int cecosCreados,
        List<String> avisos) {
}
