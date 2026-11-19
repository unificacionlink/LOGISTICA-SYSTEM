package com.tdem.logistica.modules.proveedores.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Documentos del proveedor y el estado de cada requisito (DEC-L071). */
public record DocumentosProveedorDto(List<Requisito> requisitos, List<Documento> documentos) {

    /** estado: FALTA, VIGENTE, PROXIMO_A_VENCER o VENCIDO. */
    public record Requisito(UUID requirementId, String name, String description, boolean required,
                            boolean hasExpiry, String estado, UUID supplierDocumentId, LocalDate expiresAt) {
    }

    public record Documento(UUID supplierDocumentId, String fileName, String mimeType, UUID requirementId,
                            String requisito, UUID evaluationId, LocalDate issuedAt, LocalDate expiresAt,
                            String notes, String status, String voidReason, String subidoPor,
                            OffsetDateTime createdAt, String vigencia) {
    }
}
