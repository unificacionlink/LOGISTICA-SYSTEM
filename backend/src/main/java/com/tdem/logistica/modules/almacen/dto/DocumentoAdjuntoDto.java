package com.tdem.logistica.modules.almacen.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentoAdjuntoDto(
        UUID documentId,
        String documentType,
        String fileName,
        String fileUrl,
        Instant uploadedAt) {
}
