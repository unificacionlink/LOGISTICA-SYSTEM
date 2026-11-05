package com.tdem.logistica.modules.compras.dto;

import java.util.List;
import java.util.UUID;

/** Proveedores habilitados del producto (reemplaza la lista completa). */
public record ProveedoresProductoRequest(List<UUID> supplierIds) {
}
