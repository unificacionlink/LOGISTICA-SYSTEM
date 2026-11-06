package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comparativo de cotizaciones (DEC-L047): una fila por consolidado (producto) y
 * una columna por proveedor con su ultima cotizacion vigente; los proveedores
 * con RFQ pero sin respuesta llegan como columna sin precios.
 */
public record ComparativoDto(String empresaCode, List<Fila> filas, List<Columna> proveedores) {

    public record Fila(
            UUID consolidationId,
            String consolidationCode,
            String status,
            String codigo,
            String nombreProducto,
            String atributo,
            String unidadMedida,
            BigDecimal totalQty,
            UUID adjudicadoQuotationItemId,
            String adjudicadoProveedor) {
    }

    public record Columna(
            UUID supplierId,
            String proveedorNombre,
            String ruc,
            UUID rfqId,
            String rfqCode,
            UUID quotationId,
            String supplierQuoteNumber,
            String source,
            String currencyCode,
            String paymentCondition,
            String deliveryMode,
            Integer leadDays,
            String documentoUrl,
            String documentoNombre,
            List<Precio> precios) {
    }

    public record Precio(
            UUID consolidationId,
            UUID quotationId,
            UUID quotationItemId,
            String currencyCode,
            BigDecimal qty,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            String offeredBrand,
            String offeredModel) {
    }
}
