package com.tdem.logistica.modules.almacen.dto;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;

import java.util.List;

public record DespachoRequerimientoDto(
        RequerimientoDto requerimiento,
        List<ItemPorDespacharDto> items,
        List<NotaInventarioDto> notas) {
}
