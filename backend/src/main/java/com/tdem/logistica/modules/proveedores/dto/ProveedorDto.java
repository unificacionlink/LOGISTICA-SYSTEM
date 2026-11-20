package com.tdem.logistica.modules.proveedores.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Proveedor completo. Compras sigue usando los mismos campos de siempre (los nuevos van al final). */
public record ProveedorDto(
        UUID supplierId,
        String ruc,
        String legalName,
        String contactName,
        String email,
        String whatsappPhone,
        String supplierType,
        String preferredDeliveryMode,
        boolean active,
        String tradeName,
        String contactRole,
        String phone,
        String website,
        String address,
        String city,
        String offeringDescription,
        String paymentCondition,
        Integer creditDays,
        Integer leadDays,
        String warrantyTerms,
        String status,
        String statusReason,
        OffsetDateTime submittedAt,
        OffsetDateTime decidedAt,
        OffsetDateTime createdAt,
        /** Empresa en la que se registro (DEC-L081). */
        String empresaCode) {
}
