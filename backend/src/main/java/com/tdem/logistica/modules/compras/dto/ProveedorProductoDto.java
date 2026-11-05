package com.tdem.logistica.modules.compras.dto;

import java.util.UUID;

public record ProveedorProductoDto(UUID productId, UUID supplierId, String proveedorNombre) {
}
