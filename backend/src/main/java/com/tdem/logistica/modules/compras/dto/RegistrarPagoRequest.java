package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * ANTICIPO o PAGADO con su constancia, DEC-L061. DEC-L089: el pago final
 * (PAGADO) exige la factura del proveedor (numero y documento); en el anticipo
 * es opcional. DEC-L094: constancia y factura van como enlace
 * ({@code constanciaUrl}/{@code facturaUrl}); los ids de documentos ya subidos
 * se siguen aceptando.
 */
public record RegistrarPagoRequest(
        @NotBlank(message = "Indica el estado del pago.") String estado,
        UUID documentId,
        @Size(max = 30, message = "El número de factura admite hasta 30 caracteres.") String facturaNumero,
        UUID facturaDocumentId,
        @Size(max = 500, message = "El enlace de la constancia admite hasta 500 caracteres.") String constanciaUrl,
        @Size(max = 500, message = "El enlace de la factura admite hasta 500 caracteres.") String facturaUrl) {
}
