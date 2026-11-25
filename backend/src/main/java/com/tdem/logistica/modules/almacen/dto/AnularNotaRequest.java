package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DEC-L088: anular una Nota de Inventario sin envio; el motivo es obligatorio y se audita. */
public record AnularNotaRequest(
        @NotBlank(message = "Indica el motivo de la anulación.")
        @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres.") String motivo) {
}
