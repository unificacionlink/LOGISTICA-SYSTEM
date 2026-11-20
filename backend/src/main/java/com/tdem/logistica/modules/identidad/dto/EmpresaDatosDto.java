package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

/** Datos de la empresa para la cabecera del formato F-LOG-09 (DEC-L079). */
public record EmpresaDatosDto(
        UUID id,
        String code,
        String nombre,
        String ruc,
        String direccion,
        String telefono,
        boolean tieneLogo,
        boolean tieneFirmaGerencia,
        boolean tieneFirmaLogistica) {
}
