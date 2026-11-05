package com.tdem.logistica.modules.compras.dto;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;

import java.util.List;

/** Compras de un requerimiento: sus necesidades, sus RFQ y lo pendiente por Caja Chica. */
public record ComprasDetalleDto(
        RequerimientoDto requerimiento,
        List<ItemPendienteCompraDto> items,
        List<RfqDto> rfqs,
        List<NecesidadDto> necesidades) {
}
