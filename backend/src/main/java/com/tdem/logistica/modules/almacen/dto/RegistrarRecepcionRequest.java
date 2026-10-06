package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegistrarRecepcionRequest(
        @NotEmpty(message = "No hay items para recibir.") @Valid List<RecepcionItemInput> items,
        String observation,
        GuiaInput guia) {
}
