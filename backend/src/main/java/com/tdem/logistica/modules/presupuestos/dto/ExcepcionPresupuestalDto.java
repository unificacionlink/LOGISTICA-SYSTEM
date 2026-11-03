package com.tdem.logistica.modules.presupuestos.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** REQ del CECO y semestre enviado con excepcion justificada (alerta interna de la V38.7, REQ-03). */
public record ExcepcionPresupuestalDto(UUID requestId, String requestCode, String estadoRequerimiento,
                                       String solicitante, String justificacion, List<DesviacionDto> desviaciones,
                                       Instant fecha) {
}
