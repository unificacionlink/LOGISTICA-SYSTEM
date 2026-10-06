package com.tdem.logistica.modules.proveedores.dto;

import java.util.UUID;

public record ProveedorDto(
        UUID supplierId,
        String ruc,
        String legalName,
        String contactName,
        String email,
        String whatsappPhone,
        String supplierType,
        String preferredDeliveryMode,
        boolean active) {
}
