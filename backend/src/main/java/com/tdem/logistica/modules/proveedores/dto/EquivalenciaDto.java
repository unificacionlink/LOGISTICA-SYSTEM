package com.tdem.logistica.modules.proveedores.dto;

import java.time.Instant;
import java.util.UUID;

/** Como llama el proveedor a uno de nuestros productos (DEC-L053). */
public record EquivalenciaDto(
        UUID equivalenceId,
        String supplierDescription,
        String supplierCode,
        String skuBase,
        String codigoVariante,
        String nombreProducto,
        String atributo,
        int timesUsed,
        Instant lastUsedAt) {
}
