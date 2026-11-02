package com.tdem.logistica.modules.presupuestos.dto;

import java.util.UUID;

/** Periodo presupuestal (CECO + semestre) y su presupuesto no anulado, si lo tiene. */
public record PeriodoDto(UUID periodId, UUID cecoId, String empresa, String cecoCode, String cecoNombre,
                         int anio, int semestre, boolean cerrado, UUID presupuestoId, String presupuestoCodigo) {
}
