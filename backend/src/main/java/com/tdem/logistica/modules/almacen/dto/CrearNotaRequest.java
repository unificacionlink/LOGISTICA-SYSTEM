package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CrearNotaRequest(
        @NotEmpty(message = "No hay items para despachar.") @Valid List<DespachoItemInput> items,
        @Positive(message = "Los bultos deben ser mayor a 0.") Integer packageCount,
        String itemState,
        String comments) {
}
