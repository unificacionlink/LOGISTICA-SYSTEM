package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Linea aceptada de una recepcion: entra a stock y descuenta el pendiente de la OC. */
@Entity
@Table(name = "recepcion_item")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionItem {

    @Id
    @Column(name = "receipt_item_id")
    private UUID receiptItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Recepcion recepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrdenItem ordenItem;

    @Column(name = "qty_received", nullable = false)
    private BigDecimal qtyReceived;

    @Column(name = "condition_status", nullable = false)
    private String conditionStatus;

    @Column(name = "observation")
    private String observation;

    /** Entrega directa (DEC-L084): item de requerimiento al que se entrego. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id")
    private RequerimientoItem requerimientoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "need_id")
    private NecesidadCompra necesidad;

    /** DEC-L086: entrega directa a un CECO de GOYDEL o METRIN, precio de venta de TDEM a esa empresa. */
    @Column(name = "sale_unit_price")
    private BigDecimal saleUnitPrice;
}
