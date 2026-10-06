package com.tdem.logistica.modules.compras.dto;

import java.util.List;

public record OrdenDetalleDto(OrdenDto orden, List<OrdenItemDto> items, String proveedorRuc,
                              String empresaCode) {
}
