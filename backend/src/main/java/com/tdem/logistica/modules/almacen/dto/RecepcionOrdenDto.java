package com.tdem.logistica.modules.almacen.dto;

import com.tdem.logistica.modules.compras.dto.OrdenDto;

import java.util.List;

public record RecepcionOrdenDto(
        OrdenDto orden,
        String proveedorRuc,
        List<ItemPorRecibirDto> items,
        List<RecepcionDto> recepciones,
        List<IncidenciaDto> incidencias) {
}
