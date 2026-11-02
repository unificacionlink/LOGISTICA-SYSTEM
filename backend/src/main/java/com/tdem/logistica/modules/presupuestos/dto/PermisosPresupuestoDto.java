package com.tdem.logistica.modules.presupuestos.dto;

/** Acciones que el usuario puede hacer sobre el presupuesto (la regla vive en el backend). */
public record PermisosPresupuestoDto(boolean editar, boolean emitir, boolean decidir, boolean modificar,
                                     boolean anular) {
}
