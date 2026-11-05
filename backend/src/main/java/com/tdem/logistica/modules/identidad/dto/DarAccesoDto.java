package com.tdem.logistica.modules.identidad.dto;

import java.util.List;

/** Padron actualizado y la contrasena temporal de la cuenta nueva (se muestra una sola vez). */
public record DarAccesoDto(List<PersonalAdminDto> personal, PasswordTemporalDto acceso) {
}
