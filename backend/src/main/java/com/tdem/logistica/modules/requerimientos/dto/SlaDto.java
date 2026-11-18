package com.tdem.logistica.modules.requerimientos.dto;

import java.time.LocalDate;
import java.util.List;

/** Tiempo de atencion por prioridad (DEC-L068), con la fecha tentativa si se envia {@code hoy}. */
public record SlaDto(LocalDate hoy, List<Plazo> plazos) {

    public record Plazo(String prioridad, String etiqueta, int diasHabiles, LocalDate fechaTentativa) {
    }
}
