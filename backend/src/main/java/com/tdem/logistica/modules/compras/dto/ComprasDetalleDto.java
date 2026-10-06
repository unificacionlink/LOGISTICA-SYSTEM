package com.tdem.logistica.modules.compras.dto;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;

import java.util.List;

public record ComprasDetalleDto(
        RequerimientoDto requerimiento,
        List<ItemPendienteCompraDto> items,
        List<RfqDto> rfqs) {
}
