package com.tdem.logistica.modules.maestro.dto;

import java.util.List;
import java.util.UUID;

/** Tipos -> familias y unidades de medida, para los formularios de alta. */
public record ClasificacionDto(List<Tipo> tipos, List<Unidad> unidades) {

    public record Tipo(UUID id, String code, String nombre, List<Familia> familias) {
    }

    public record Familia(UUID id, String code, String nombre) {
    }

    public record Unidad(UUID id, String code, String nombre) {
    }
}
