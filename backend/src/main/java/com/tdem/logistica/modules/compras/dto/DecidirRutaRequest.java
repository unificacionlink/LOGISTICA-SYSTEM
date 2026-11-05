package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;

/** COMPRA (entra al consolidado) o CAJA_CHICA. */
public record DecidirRutaRequest(@NotBlank(message = "Indica la ruta.") String ruta) {
}
