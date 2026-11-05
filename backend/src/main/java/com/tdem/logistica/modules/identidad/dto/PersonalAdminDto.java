package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

/**
 * Persona del padron (DEC-L041). {@code correo}: el de su cuenta si la tiene,
 * si no el que trae RR.HH. o el Excel. {@code userId} null = sin cuenta.
 */
public record PersonalAdminDto(
        UUID id,
        String empresaCode,
        String dni,
        String nombre,
        String correo,
        String cargo,
        String cecoCode,
        String cecoNombre,
        String fuente,
        UUID userId) {
}
