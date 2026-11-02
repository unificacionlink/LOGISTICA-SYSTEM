package com.tdem.logistica.modules.maestro.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Dias calendario de atencion del SKU; null lo deja sin configurar. */
public record PlazoAtencionRequest(
        @Min(value = 0, message = "Ingresa dias calendario entre 0 y 3650.")
        @Max(value = 3650, message = "Ingresa dias calendario entre 0 y 3650.") Integer dias) {
}
