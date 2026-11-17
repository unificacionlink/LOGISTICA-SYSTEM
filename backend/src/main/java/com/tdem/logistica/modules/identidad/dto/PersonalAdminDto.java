package com.tdem.logistica.modules.identidad.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Persona del padron (DEC-L041). {@code correo}: el de su cuenta si la tiene,
 * si no el que trae RR.HH. o el Excel. {@code userId} null = sin cuenta.
 * {@code estado}: ACTIVO, INACTIVO (lo hizo un ADMIN: {@code inactivadoPor}) o
 * CESADO (lo trajo RR.HH.); {@code inactivadoEn} es la fecha (DEC-L064).
 * {@code eliminable}: sin cuenta y, si viene de RR.HH., cesado (DEC-L065).
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
        String inactivadoPor,
        String estado,
        boolean eliminable) {
}
