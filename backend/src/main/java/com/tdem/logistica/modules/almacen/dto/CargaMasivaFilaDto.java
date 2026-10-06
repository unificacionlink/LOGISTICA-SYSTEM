package com.tdem.logistica.modules.almacen.dto;

import java.math.BigDecimal;

/** RF-031: una fila del Excel de ingresos con su resultado de validacion. */
public record CargaMasivaFilaDto(
        int fila,
        String orden,
        String proveedor,
        String sku,
        String producto,
        BigDecimal cantidad,
        BigDecimal pendiente,
        String estadoProducto,
        String guia,
        String error) {
}
