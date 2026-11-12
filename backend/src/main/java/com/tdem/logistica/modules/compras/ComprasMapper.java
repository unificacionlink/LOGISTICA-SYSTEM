package com.tdem.logistica.modules.compras;

import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.compras.OrigenCompras.ReqRef;
import com.tdem.logistica.modules.compras.dto.*;

import java.math.BigDecimal;
import java.util.List;

public final class ComprasMapper {

    private ComprasMapper() {
    }

    public static ItemPendienteCompraDto toDto(RequerimientoItem i, BigDecimal cantidadPendiente) {
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

    public static RfqDto toDto(Rfq r, List<ReqRef> refs) {
        return new RfqDto(
                r.getRfqId(),
                r.getRfqCode(),
                OrigenCompras.unico(refs),
                OrigenCompras.codigos(refs),
                r.getProveedor().getSupplierId(),
                r.getProveedor().getLegalName(),
                r.getEmpresa().getCode(),
                r.getIssueDate(),
                r.getPaymentCondition(),
                r.getDeliveryMode(),
                r.getDeliveryLocation(),
                r.getRequestedLeadDays(),
                r.getCurrencyRequested() != null ? r.getCurrencyRequested().getCurrencyCode() : null,
                r.getStatus(),
                r.getCreatedAt(),
                r.getSentAt(),
                r.getSentChannel());
    }

    public static RfqItemDto toDto(RfqItem i) {
        Consolidacion c = i.getConsolidacion();
        return new RfqItemDto(
                i.getRfqItemId(),
                c != null ? c.getConsolidationId() : null,
                c != null ? c.getConsolidationCode() : null,
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

    public static CotizacionDto toDto(Cotizacion c, List<CotizacionItemDto> items) {
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
                c.getSource(),
                items);
    }

    public static OrdenDto toDto(OrdenAbastecimiento o, List<ReqRef> refs) {
        return new OrdenDto(
                o.getOrderId(),
                o.getOrderCode(),
                OrigenCompras.unico(refs),
                OrigenCompras.codigos(refs),
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
        return toDto(i, null);
    }

    /** @param referenciaProveedor descripcion del proveedor en su cotizacion (DEC-L053), o null. */
    public static OrdenItemDto toDto(OrdenItem i, String referenciaProveedor) {
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
                i.getLineTotal(),
                referenciaProveedor);
    }

    /** qty = lo que queda por atender en esa ruta (en Caja Chica, descontado lo ya comprado). */
    public static NecesidadDto toDto(NecesidadCompra n, BigDecimal qty) {
        Requerimiento r = n.getRequerimientoItem().getRequerimiento();
        Consolidacion c = n.getConsolidacion();
        return new NecesidadDto(
                n.getNeedId(),
                r.getRequestId(),
                r.getRequestCode(),
                r.getEmpresa().getCode(),
                r.getCeco().getCode(),
                r.getCeco().getName(),
                r.getPriority(),
                r.getRequiredDate(),
                n.getProducto().getSkuBase(),
                n.getVariante() != null ? n.getVariante().getInternalVariantCode() : null,
                n.getProducto().getName(),
                n.getVariante() != null ? n.getVariante().getAttributeValue() : null,
                n.getUom().getCode(),
                qty,
                n.getStatus(),
                n.getSuggestedReason(),
                c != null ? c.getConsolidationId() : null,
                c != null ? c.getConsolidationCode() : null,
                c != null ? c.getStatus() : null,
                n.getCreatedAt(),
                r.isPettyCashSuggested() ? r.getPettyCashSuggestionNote() : null);
    }

    public static OrigenDto toOrigen(NecesidadCompra n) {
        Requerimiento r = n.getRequerimientoItem().getRequerimiento();
        return new OrigenDto(n.getNeedId(), r.getRequestId(), r.getRequestCode(), r.getCeco().getCode(),
                r.getCeco().getName(), r.getPriority(), r.getRequiredDate(), n.getQty());
    }

    public static AdjudicacionDto toAdjudicacion(CotizacionItem ci) {
        if (ci == null) {
            return null;
        }
        Cotizacion q = ci.getCotizacion();
        return new AdjudicacionDto(ci.getQuotationItemId(), q.getQuotationId(), q.getProveedor().getLegalName(),
                q.getMoneda().getCurrencyCode(), ci.getQty(), ci.getUnitCost(), ci.getLineTotal());
    }

    public static ConsolidadoDto toDto(Consolidacion c, List<OrigenDto> origenes, List<String> proveedores,
                                       int cotizaciones, BigDecimal cantidadCotizada) {
        return new ConsolidadoDto(
                c.getConsolidationId(),
                c.getConsolidationCode(),
                c.getEmpresa().getCode(),
                c.getProducto().getProductId(),
                c.getProducto().getSkuBase(),
                c.getVariante() != null ? c.getVariante().getInternalVariantCode() : null,
                c.getProducto().getName(),
                c.getVariante() != null ? c.getVariante().getAttributeValue() : null,
                c.getUom().getCode(),
                c.getTotalQty(),
                c.getStatus(),
                origenes,
                proveedores,
                cotizaciones,
                cantidadCotizada,
                toAdjudicacion(c.getAdjudicado()),
                c.getCreatedAt());
    }
}
