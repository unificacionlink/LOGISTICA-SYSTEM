package com.tdem.logistica.modules.requerimientos.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Avance por producto de un requerimiento (DEC-L048): cada item avanza por su cuenta. */
public record AvanceRequerimientoDto(List<Item> items) {

    /**
     * @param etapaCompra RUTA, CAJA_CHICA, POR_COTIZAR, EN_COTIZACION, ADJUDICADO u OC; null si no hay compra activa
     */
    public record Item(
            UUID requestItemId,
            String codigo,
            String nombreProducto,
            String atributo,
            String unidadMedida,
            BigDecimal solicitado,
            BigDecimal entregado,
            BigDecimal enCamino,
            BigDecimal listo,
            BigDecimal enCompra,
            BigDecimal recibidoCompra,
            String etapaCompra,
            int ofertas,
            UUID consolidationId,
            String consolidationCode,
            UUID orderId,
            String orderCode) {
    }
}
