package com.tdem.logistica.modules.maestro.dto;

import java.util.List;
import java.util.UUID;

public record ProductoCreadoDto(UUID productId, String skuBase, String nombre, List<String> variantes) {
}
