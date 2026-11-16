package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** ANTICIPO o PAGADO con su constancia (documento CONSTANCIA_PAGO ya subido), DEC-L061. */
public record RegistrarPagoRequest(
        @NotBlank(message = "Indica el estado del pago.") String estado,
        UUID documentId) {
}
