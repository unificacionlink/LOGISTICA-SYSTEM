package com.tdem.logistica.modules.identidad.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Persona del padron (DEC-L041). {@code correo}: el de su cuenta si la tiene,
 * si no el que trae RR.HH. o el Excel. {@code userId} null = sin cuenta.
 * Inactiva (DEC-L064): {@code inactivadoEn} con fecha; {@code inactivadoPor}
 * null = cese de RR.HH., si no el nombre del ADMIN que la inactivo.
 */
public record PersonalAdminDto(
        UUID id,
        String empresaCode,
        String dni,
        String nombre,
        String correo,
        String cargo,
        UUID cecoId,
        String cecoCode,
        String cecoNombre,
        String fuente,
        UUID userId,
        boolean activo,
        Instant inactivadoEn,
        String inactivadoPor) {
}
