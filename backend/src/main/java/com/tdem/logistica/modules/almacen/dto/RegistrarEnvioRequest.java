package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DEC-L093: basta el enlace del ticket de envio; lo demas es opcional (modalidad por defecto AGENCIA). */
public record RegistrarEnvioRequest(
        String deliveryMode,
        String carrierName,
        String trackingCode,
        @Positive(message = "Los bultos deben ser mayor a 0.") Integer packageCount,
        @DecimalMin(value = "0", message = "El peso no puede ser negativo.")
        @Digits(integer = 6, fraction = 3, message = "Peso invalido: hasta 6 digitos y 3 decimales.") BigDecimal declaredWeight,
        String currencyCode,
        @DecimalMin(value = "0", message = "El costo no puede ser negativo.") @Digits(integer = 9, fraction = 2, message = "Monto invalido: hasta 9 digitos y 2 decimales.") BigDecimal shippingCostNet,
        @DecimalMin(value = "0", message = "El IGV no puede ser negativo.") @Digits(integer = 9, fraction = 2, message = "Monto invalido: hasta 9 digitos y 2 decimales.") BigDecimal shippingTaxAmount,
        LocalDate shipDate,
        LocalDate expectedDate,
        @NotBlank(message = "Pega el enlace del ticket de envío.")
        @Size(max = 500, message = "El enlace admite hasta 500 caracteres.") String ticketUrl) {
}
