package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de la ficha del proveedor, para crear o editar (DEC-L069). El estado no se edita aqui. */
public record GuardarProveedorRequest(
        @NotBlank(message = "El RUC es obligatorio.")
        @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 digitos.")
        String ruc,
        @NotBlank(message = "La razon social es obligatoria.") @Size(max = 220) String legalName,
        @Size(max = 220) String tradeName,
        @Size(max = 150) String contactName,
        @Size(max = 120) String contactRole,
        @Email(message = "El correo no es valido.") @Size(max = 180) String email,
        @Size(max = 30) String whatsappPhone,
        @Size(max = 30) String phone,
        @Size(max = 200) String website,
        @Size(max = 250) String address,
        @Size(max = 120) String city,
        @Pattern(regexp = "CRITICO|NO_CRITICO", message = "Tipo de proveedor invalido.") String supplierType,
        @Pattern(regexp = "AGENCIA|ALMACEN|RECOJO", message = "Modo de entrega invalido.") String preferredDeliveryMode,
        @Size(max = 1000) String offeringDescription,
        @Size(max = 150) String paymentCondition,
        @Min(0) @Max(365) Integer creditDays,
        @Min(0) @Max(365) Integer leadDays,
        @Size(max = 500) String warrantyTerms) {
}
