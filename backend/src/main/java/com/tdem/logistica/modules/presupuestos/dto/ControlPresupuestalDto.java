package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ultimo control presupuestal del REQ (al enviarlo). estado = CUBIERTO | EXCEPCION_JUSTIFICADA. */
public record ControlPresupuestalDto(String estado, String periodo, UUID presupuestoId, String presupuestoCodigo,
                                     BigDecimal monto, String justificacion, List<DesviacionDto> desviaciones,
                                     String verificadoPor, Instant fecha) {
}
