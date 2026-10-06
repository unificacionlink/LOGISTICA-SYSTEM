package com.tdem.logistica.modules.compras;

import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.compras.dto.*;

public final class ComprasMapper {

    private ComprasMapper() {
    }

    public static ItemPendienteCompraDto toDto(RequerimientoItem i, java.math.BigDecimal cantidadPendiente) {
        return new ItemPendienteCompraDto(
                i.getRequestItemId(),
                i.getProducto().getSkuBase(),
                i.getProducto().getName(),
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getUom().getCode(),
                i.getQtyRequested(),
                cantidadPendiente);
    }

    public static MonedaDto toDto(Moneda m) {
        return new MonedaDto(m.getCurrencyCode(), m.getName());
    }

    public static RfqDto toDto(Rfq r) {
        return new RfqDto(
                r.getRfqId(),
                r.getRfqCode(),
                r.getRequerimiento().getRequestId(),
                r.getRequerimiento().getRequestCode(),
                r.getProveedor().getSupplierId(),
                r.getProveedor().getLegalName(),
                r.getIssueDate(),
                r.getPaymentCondition(),
                r.getDeliveryMode(),
                r.getDeliveryLocation(),
                r.getRequestedLeadDays(),
                r.getCurrencyRequested() != null ? r.getCurrencyRequested().getCurrencyCode() : null,
                r.getStatus(),
                r.getCreatedAt());
    }

    public static RfqItemDto toDto(RfqItem i) {
        return new RfqItemDto(
                i.getRfqItemId(),
                i.getRequerimientoItem().getRequestItemId(),
                i.getProducto().getSkuBase(),
                i.getProducto().getName(),
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getUom().getCode(),
                i.getQty(),
                i.getTechnicalSpecification());
    }

    public static CotizacionItemDto toDto(CotizacionItem i) {
        return new CotizacionItemDto(
                i.getQuotationItemId(),
                i.getRfqItem().getRfqItemId(),
                i.getProducto().getSkuBase(),
                i.getProducto().getName(),
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getQty(),
                i.getUnitCost(),
                i.getLineTotal(),
                i.getOfferedBrand(),
                i.getOfferedModel());
    }

    public static CotizacionDto toDto(Cotizacion c, java.util.List<CotizacionItemDto> items) {
        return new CotizacionDto(
                c.getQuotationId(),
                c.getRfq().getRfqId(),
                c.getProveedor().getSupplierId(),
                c.getProveedor().getLegalName(),
                c.getSupplierQuoteNumber(),
                c.getIssueDate(),
                c.getExpirationDate(),
                c.getMoneda().getCurrencyCode(),
                c.getSubtotal(),
                c.getTaxAmount(),
                c.getTotal(),
                c.getPaymentCondition(),
                c.getDeliveryMode(),
                c.getLeadDays(),
                c.getStatus(),
                c.getDocumento() != null ? c.getDocumento().getFileUrl() : null,
                items);
    }

    public static OrdenDto toDto(OrdenAbastecimiento o) {
        return new OrdenDto(
                o.getOrderId(),
                o.getOrderCode(),
                o.getCotizacion().getRfq().getRequerimiento().getRequestId(),
                o.getCotizacion().getRfq().getRequerimiento().getRequestCode(),
                o.getProveedor().getLegalName(),
                o.getOrderType(),
                o.getIssueDate(),
                o.getPaymentCondition(),
                o.getDeliveryMode(),
                o.getDeliveryLocation(),
                o.getMoneda().getCurrencyCode(),
                o.getTotal(),
                o.getStatus(),
                o.getCreatedAt());
    }

    public static OrdenItemDto toDto(OrdenItem i) {
        // SKU/atributo/variante salen de lo efectivamente cotizado y ordenado
        // (i.getProducto()/i.getVariante()), no del requerimiento_item original:
        // la cotizacion ganadora puede ofrecer una marca/modelo distinto.
        return new OrdenItemDto(
                i.getOrderItemId(),
                i.getProducto() != null ? i.getProducto().getSkuBase() : null,
                i.getProducto() != null ? i.getProducto().getName() : null,
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getDescription(),
                i.getUom() != null ? i.getUom().getCode() : null,
                i.getQtyOrdered(),
                i.getUnitCost(),
                i.getLineTotal());
    }
}
