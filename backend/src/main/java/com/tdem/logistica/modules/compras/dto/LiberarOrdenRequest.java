package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.Size;

/** Nota opcional al liberar la OC para recepcion (DEC-L061). */
public record LiberarOrdenRequest(
        @Size(max = 300, message = "La nota admite hasta 300 caracteres.") String nota) {
}
