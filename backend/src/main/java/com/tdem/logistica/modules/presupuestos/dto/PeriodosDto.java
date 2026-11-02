package com.tdem.logistica.modules.presupuestos.dto;

import java.util.List;

/**
 * Periodos del ambito del usuario. {@code cecosConfigurables} solo para PYC;
 * {@code puedeCrear} = el usuario redacta presupuestos.
 */
public record PeriodosDto(List<PeriodoDto> periodos, List<CecoOpcionDto> cecosConfigurables, boolean puedeCrear) {
}
