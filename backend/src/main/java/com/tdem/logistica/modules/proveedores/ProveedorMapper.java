package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.ProveedorDto;

public final class ProveedorMapper {

    private ProveedorMapper() {
    }

    public static ProveedorDto toDto(Proveedor p) {
        return new ProveedorDto(
                p.getSupplierId(),
                p.getRuc(),
                p.getLegalName(),
                p.getContactName(),
                p.getEmail(),
                p.getWhatsappPhone(),
                p.getSupplierType(),
                p.getPreferredDeliveryMode(),
                p.isActive(),
                p.getTradeName(),
                p.getContactRole(),
                p.getPhone(),
                p.getWebsite(),
                p.getAddress(),
                p.getCity(),
                p.getOfferingDescription(),
                p.getPaymentCondition(),
                p.getCreditDays(),
                p.getLeadDays(),
                p.getWarrantyTerms(),
                p.getStatus(),
                p.getStatusReason(),
                p.getSubmittedAt(),
                p.getDecidedAt(),
                p.getCreatedAt());
    }
}
