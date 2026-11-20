package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta o edicion de una cuenta bancaria del proveedor (DEC-L078). */
public record GuardarCuentaBancariaRequest(
        @NotBlank(message = "Indica el banco.") @Size(max = 80) String banco,
        @NotBlank @Pattern(regexp = "AHORROS|CORRIENTE|DETRACCION|OTRA", message = "Tipo de cuenta invalido.") String tipo,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Moneda invalida.") String moneda,
        @NotBlank(message = "Indica el numero de cuenta.")
        @Pattern(regexp = "[0-9 -]{6,40}", message = "El numero de cuenta solo lleva digitos, espacios o guiones.")
        String numero,
        @Pattern(regexp = "|[0-9 -]{20,30}", message = "El CCI tiene 20 digitos.") String cci,
        boolean principal) {
}
