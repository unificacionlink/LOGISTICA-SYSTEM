package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila del listado. {@code total} en PEN neto; {@code lineasSinPrecio} > 0 = total incompleto. */
public record PresupuestoResumenDto(UUID id, String codigo, String empresa, String cecoCode, String cecoNombre,
                                    int anio, int semestre, String estado, String solicitante, String aprobador,
                                    BigDecimal total, int lineasSinPrecio, boolean porDecidir, Instant actualizadoEn) {
}
