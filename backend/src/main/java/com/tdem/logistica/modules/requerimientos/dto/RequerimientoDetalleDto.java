package com.tdem.logistica.modules.requerimientos.dto;

import com.tdem.logistica.modules.presupuestos.dto.ControlPresupuestalDto;

import java.util.List;
import java.util.UUID;

/**
 * {@code observacion}: motivo vigente si el REQ esta OBSERVADO (RF-048); si no, null.
 * {@code controlPresupuestal}: ultimo control al enviarlo (DEC-L038); null si no se valido.
 */
public record RequerimientoDetalleDto(
        RequerimientoDto requerimiento,
        List<RequerimientoItemDto> items,
        UUID pendingApprovalId,
        ObservacionDto observacion,
        ControlPresupuestalDto controlPresupuestal) {
}
