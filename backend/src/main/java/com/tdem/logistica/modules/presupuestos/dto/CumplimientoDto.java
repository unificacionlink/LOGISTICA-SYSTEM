package com.tdem.logistica.modules.presupuestos.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cumplimiento por cantidades y precios congelados de la version vigente (RES-01/RES-02). */
public record CumplimientoDto(UUID presupuestoId, String codigo, String empresa, String cecoCode, String cecoNombre,
                              String periodo, int version, Instant corte, boolean conCompra, boolean puedeDescargar,
                              List<CumplimientoFilaDto> filas, List<ResumenFamiliaDto> resumen) {
}
