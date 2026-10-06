package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrarCotizacionRequest(
        String supplierQuoteNumber,
        LocalDate issueDate,
        LocalDate expirationDate,
        @NotNull(message = "Selecciona una moneda.") String currencyCode,
        @NotBlank(message = "La condicion de pago es obligatoria.") String paymentCondition,
        @NotBlank(message = "La modalidad de entrega es obligatoria.") String deliveryMode,
        Integer leadDays,
        UUID documentId,
        @NotEmpty(message = "Agrega al menos un item cotizado.")
        @Valid
        List<CotizacionItemInput> items) {
}
