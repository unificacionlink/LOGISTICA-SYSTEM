package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * {@code supplierIds}: a quienes pedir la cotizacion (DEC-L082). Sin ellos se
 * pide a todos los proveedores habilitados de cada producto, como antes.
 */
public record PrepararSolicitudesRequest(
        @NotEmpty(message = "Selecciona al menos un consolidado.") List<UUID> consolidationIds,
        List<UUID> supplierIds) {
}
