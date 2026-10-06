package com.tdem.logistica.modules.identidad.dto;

/** Contrasena temporal: se muestra una sola vez a ADMIN y nunca se vuelve a leer. */
public record PasswordTemporalDto(String correo, String password) {
}
