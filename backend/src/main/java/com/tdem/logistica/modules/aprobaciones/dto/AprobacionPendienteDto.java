package com.tdem.logistica.modules.aprobaciones.dto;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;

import java.util.UUID;

public record AprobacionPendienteDto(UUID approvalId, RequerimientoDto requerimiento) {
}
