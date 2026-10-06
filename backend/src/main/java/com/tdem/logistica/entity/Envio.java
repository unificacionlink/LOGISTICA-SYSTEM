package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Envio de una NI al destino (RF-075/076/078). received_at = recepcion en DESTINO, no la de compra. */
@Entity
@Table(name = "envio")
@Getter
@Setter
@NoArgsConstructor
public class Envio {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_type", nullable = false)
    private String shipmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrdenAbastecimiento orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private Requerimiento requerimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private Salida salida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_note_id")
    private NotaInventario notaInventario;

    @Column(name = "delivery_point_id")
    private UUID deliveryPointId;

    @Column(name = "delivery_mode", nullable = false)
    private String deliveryMode;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "package_count")
    private Integer packageCount;

    @Column(name = "declared_weight")
    private BigDecimal declaredWeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code")
    private Moneda moneda;

    @Column(name = "shipping_cost_net")
    private BigDecimal shippingCostNet;

    @Column(name = "shipping_tax_amount")
    private BigDecimal shippingTaxAmount;

    @Column(name = "shipping_cost_total")
    private BigDecimal shippingCostTotal;

    @Column(name = "ship_date")
    private LocalDate shipDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "received_at")
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private Usuario receivedBy;

    @Column(name = "delivery_observation")
    private String deliveryObservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
