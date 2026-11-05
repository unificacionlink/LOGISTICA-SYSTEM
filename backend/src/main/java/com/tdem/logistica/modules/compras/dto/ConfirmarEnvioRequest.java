package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotBlank;

/** CORREO, WHATSAPP u OTRO. */
public record ConfirmarEnvioRequest(@NotBlank(message = "Indica el canal de envio.") String canal) {
}
