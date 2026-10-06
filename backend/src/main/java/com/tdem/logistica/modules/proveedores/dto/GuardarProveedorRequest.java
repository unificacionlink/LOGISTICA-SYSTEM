package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Usado tanto para crear como para editar un proveedor. */
public record GuardarProveedorRequest(
        @NotBlank(message = "El RUC es obligatorio.")
        @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 digitos.")
        String ruc,
        @NotBlank(message = "La razon social es obligatoria.") String legalName,
        String contactName,
        String email,
        String whatsappPhone,
        String supplierType,
        String preferredDeliveryMode,
        boolean active) {
}
