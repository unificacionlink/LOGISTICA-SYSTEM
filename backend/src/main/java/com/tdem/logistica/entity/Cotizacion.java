package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** source siempre MANUAL en este lote: no hay pipeline de OCR. */
@Entity
@Table(name = "cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion {

    @Id
    @Column(name = "quotation_id")
    private UUID quotationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "supplier_quote_number")
    private String supplierQuoteNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", nullable = false)
    private Moneda moneda;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "payment_condition")
    private String paymentCondition;

    @Column(name = "delivery_mode")
    private String deliveryMode;

    @Column(name = "lead_days")
    private Integer leadDays;

    @Column(name = "source", nullable = false)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Documento documento;

    @Column(name = "status", nullable = false)
    private String status;

    /** V10: la llena la BD al insertar (default now()); mide el tiempo de respuesta. */
    @Column(name = "received_at", insertable = false, updatable = false)
    private java.time.Instant receivedAt;
}
