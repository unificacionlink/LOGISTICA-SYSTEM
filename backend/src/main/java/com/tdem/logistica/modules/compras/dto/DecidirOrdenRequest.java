package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** APROBADO o RECHAZADO; el rechazo exige motivo (DEC-L061). */
public record DecidirOrdenRequest(
        @NotBlank(message = "La decision es obligatoria.") String decision,
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres.") String motivo) {
}
