package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * ANTICIPO o PAGADO con su constancia (documento CONSTANCIA_PAGO ya subido),
 * DEC-L061. DEC-L089: el pago final (PAGADO) exige la factura del proveedor
 * (numero y archivo FACTURA_PDF ya subido); en el anticipo es opcional.
 */
public record RegistrarPagoRequest(
        @NotBlank(message = "Indica el estado del pago.") String estado,
        UUID documentId,
        @Size(max = 30, message = "El número de factura admite hasta 30 caracteres.") String facturaNumero,
        UUID facturaDocumentId) {
}
