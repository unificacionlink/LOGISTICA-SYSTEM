package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CrearRfqRequest(
        @NotNull(message = "Selecciona un proveedor.") UUID supplierId,
        String paymentCondition,
        String deliveryMode,
        String deliveryLocation,
        Integer requestedLeadDays,
        String currencyRequested,
        @NotEmpty(message = "Selecciona al menos un item a cotizar.")
        @Valid
        List<RfqItemInput> items) {
}
