package com.tdem.logistica.modules.compras.dto;

import java.util.UUID;

/** supplierId null = quitar el proveedor habitual del producto. */
public record AsignarProveedorHabitualRequest(UUID supplierId) {
}
