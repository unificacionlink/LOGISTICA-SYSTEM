package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orden de compra. En este lote quotation_id es siempre no-nulo (order_type
 * siempre OC_NORMAL): las rutas CONVENIO/CAJA_CHICA no existen todavia
 * (ver DEC-L013).
 */
@Entity
@Table(name = "orden_abastecimiento")
@Getter
@Setter
@NoArgsConstructor
public class OrdenAbastecimiento {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_code", nullable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Cotizacion cotizacion;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "payment_condition", nullable = false)
    private String paymentCondition;

    @Column(name = "delivery_mode", nullable = false)
    private String deliveryMode;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", nullable = false)
    private Moneda moneda;

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
