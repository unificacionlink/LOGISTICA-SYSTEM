package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.entity.*;
import com.tdem.logistica.modules.almacen.dto.*;

import java.math.BigDecimal;
import java.util.List;

final class AlmacenMapper {

    private AlmacenMapper() {
    }

    static ItemPorRecibirDto toItemPorRecibir(OrdenItem i, BigDecimal recibido) {
        return new ItemPorRecibirDto(
                i.getOrderItemId(),
                i.getProducto() != null ? i.getProducto().getSkuBase() : null,
                i.getProducto() != null ? i.getProducto().getName() : null,
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getDescription(),
                i.getUom() != null ? i.getUom().getCode() : null,
                i.getQtyOrdered(),
                recibido,
                i.getQtyOrdered().subtract(recibido).max(BigDecimal.ZERO));
    }

    static RecepcionDto toDto(Recepcion r, List<RecepcionItem> items, GuiaRemision guia) {
        return new RecepcionDto(
                r.getReceiptId(),
                r.getReceiptNumber(),
                r.getReceiptDate(),
                r.getStatus(),
                r.getReceivedBy().getDisplayName(),
                r.getObservation(),
                guia != null ? guia.getSeries() + "-" + guia.getNumber() : null,
                items.stream()
                        .map(i -> new RecepcionItemDto(
                                i.getOrdenItem().getOrderItemId(),
                                i.getOrdenItem().getDescription(),
                                i.getQtyReceived(),
                                i.getConditionStatus(),
                                i.getObservation()))
                        .toList());
    }

    static IncidenciaDto toDto(IncidenciaRecepcion i) {
        return new IncidenciaDto(
                i.getReceiptIssueId(),
                i.getRecepcion().getReceiptNumber(),
                i.getOrdenItem().getOrderItemId(),
                i.getOrdenItem().getDescription(),
                i.getIssueType(),
                i.getQtyAffected(),
                i.getDescription(),
                i.getSupplierAction(),
                i.getStatus(),
                i.getCreatedAt(),
                i.getClosedAt());
    }

    static EnvioDto toDto(Envio e) {
        if (e == null) {
            return null;
        }
        return new EnvioDto(
                e.getShipmentId(),
                e.getDeliveryMode(),
                e.getCarrierName(),
                e.getTrackingCode(),
                e.getPackageCount(),
                e.getDeclaredWeight(),
                e.getMoneda() != null ? e.getMoneda().getCurrencyCode() : null,
                e.getShippingCostNet(),
                e.getShippingTaxAmount(),
                e.getShippingCostTotal(),
                e.getShipDate(),
                e.getExpectedDate(),
                e.getStatus(),
                e.getReceivedAt(),
                e.getReceivedBy() != null ? e.getReceivedBy().getDisplayName() : null,
                e.getDeliveryObservation(),
                e.getConformityStatus());
    }

    static NotaInventarioDto toDto(NotaInventario n, Envio envio) {
        return new NotaInventarioDto(
                n.getInventoryNoteId(),
                n.getInventoryNoteCode(),
                n.getRequerimiento().getRequestId(),
                n.getRequerimiento().getRequestCode(),
                n.getEmpresa().getCode(),
                n.getCecoDestino() != null ? n.getCecoDestino().getName() : null,
                n.getMovementType(),
                n.getPickingDate(),
                n.getPickingUser().getDisplayName(),
                n.getItemState(),
                n.getTotalItemQty(),
                n.getTotalValueSnapshot(),
                n.getPackageCount(),
                n.getComments(),
                n.getStatus(),
                n.getIssuedAt(),
                toDto(envio),
                n.getAlmacenOrigen() != null ? n.getAlmacenOrigen().getName() : null,
                n.getCecoDestino() != null ? n.getCecoDestino().getCode() : null,
                n.getRequerimiento().getRequester() != null ? n.getRequerimiento().getRequester().getDisplayName() : null,
                n.getPickedAt(),
                n.getPackedAt(),
                n.getPackingEvidenceUrl());
    }

    static NotaItemDto toDto(SalidaItem i) {
        return new NotaItemDto(
                i.getProducto().getSkuBase(),
                i.getProducto().getName(),
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getQty(),
                i.getPurchaseCostReference(),
                i.getIssueItemId(),
                i.getDestReceivedQty(),
                i.getDestAcceptedQty(),
                i.getDestObservedQty(),
                i.getDestReturnedQty());
    }

    static String sku(SalidaItem i) {
        return i.getVariante() != null ? i.getVariante().getInternalVariantCode() : i.getProducto().getSkuBase();
    }

    static DestinoDtos.RecepcionDto toDto(RecepcionDestino r, List<DestinoDtos.LineaDto> lineas) {
        return new DestinoDtos.RecepcionDto(r.getDestinationReceiptId(), r.getReceiptNumber(), r.getCreatedAt(),
                r.getReceivedBy().getDisplayName(), r.getResult(), r.getObservation(), r.getConformityUrl(),
                r.getDocumento() != null ? r.getDocumento().getFileUrl() : null,
                r.getDocumento() != null ? r.getDocumento().getFileName() : null, lineas);
    }

    static DestinoDtos.ResolucionDto toDto(ResolucionDestino r) {
        return new DestinoDtos.ResolucionDto(r.getResolutionId(), sku(r.getSalidaItem()),
                r.getSalidaItem().getProducto().getName(), r.getQty(), r.getAction(), r.getEvidenceUrl(), r.getStatus(),
                r.getCreatedAt(), r.getCreatedBy().getDisplayName(), r.getReceivedAt(),
                r.getReceivedBy() != null ? r.getReceivedBy().getDisplayName() : null, r.getReturnEvidenceUrl());
    }

    static DocumentoAdjuntoDto toDto(Documento d) {
        return new DocumentoAdjuntoDto(
                d.getDocumentId(), d.getDocumentType(), d.getFileName(), d.getFileUrl(), d.getUploadedAt());
    }
}
