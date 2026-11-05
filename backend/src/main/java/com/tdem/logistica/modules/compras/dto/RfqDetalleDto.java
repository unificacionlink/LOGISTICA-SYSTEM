package com.tdem.logistica.modules.compras.dto;

import java.util.List;

public record RfqDetalleDto(RfqDto rfq, List<RfqItemDto> items, List<CotizacionDto> cotizaciones, String proveedorRuc,
                           String empresaCode, String proveedorEmail, String proveedorTelefono,
                           String proveedorContacto) {
}
