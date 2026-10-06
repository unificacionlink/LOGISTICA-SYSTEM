package com.tdem.logistica.modules.compras.dto;

import java.util.UUID;

public record DocumentoDto(UUID documentId, String fileUrl, String fileName) {
}
