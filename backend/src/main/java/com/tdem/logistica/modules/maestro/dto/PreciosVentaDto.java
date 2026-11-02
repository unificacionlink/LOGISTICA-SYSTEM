package com.tdem.logistica.modules.maestro.dto;

import com.tdem.logistica.modules.stock.dto.EmpresaDto;

import java.util.List;
import java.util.UUID;

/** Precios vigentes de la empresa elegida y las empresas en las que el usuario puede registrarlos. */
public record PreciosVentaDto(List<EmpresaDto> empresas, UUID companyId, List<PrecioVentaDto> precios) {
}
