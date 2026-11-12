package com.tdem.logistica.modules.requerimientos.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RequerimientoItemDto(
        UUID id,
        String skuBase,
        String nombreProducto,
        String atributo,
        String codigoVariante,
        String unidadMedida,
        BigDecimal cantidad,
        String requisitoTecnico,
        String observacion,
        /** DEC-L054: unidades sugeridas para la caja chica del proyecto. */
        BigDecimal cantidadCajaChica) {
}
