package com.tdem.logistica.modules.requerimientos.dto;

import java.util.List;
import java.util.UUID;

/** {@code observacion}: motivo vigente si el REQ esta OBSERVADO (RF-048); si no, null. */
public record RequerimientoDetalleDto(
        RequerimientoDto requerimiento,
        List<RequerimientoItemDto> items,
        UUID pendingApprovalId,
        ObservacionDto observacion) {
}
