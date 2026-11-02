package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PresupuestoDetalleDto(UUID id, String codigo, UUID periodId, String empresa, String cecoCode,
                                    String cecoNombre, int anio, int semestre, boolean periodoCerrado, String estado,
                                    UUID solicitanteId, String solicitante, String aprobador, String observaciones,
                                    String motivoDecision, Instant emitidoEn, Integer versionVigente,
                                    Instant aprobadoEn, String aprobadoPor, List<LineaPresupuestoDto> lineas,
                                    BigDecimal total, int lineasSinPrecio, PermisosPresupuestoDto permisos,
                                    List<HistorialPresupuestoDto> historial) {
}
