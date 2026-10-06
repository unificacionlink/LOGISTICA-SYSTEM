package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * RFQ (Request For Quote) a un proveedor, armada desde items de UN
 * requerimiento (sin consolidacion cruzada RN-002/004, ver DEC-L013).
 */
@Entity
@Table(name = "rfq")
@Getter
@Setter
@NoArgsConstructor
public class Rfq {

    @Id
    @Column(name = "rfq_id")
    private UUID rfqId;

    @Column(name = "rfq_code", nullable = false, unique = true)
    private String rfqCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Requerimiento requerimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "payment_condition")
    private String paymentCondition;

    @Column(name = "delivery_mode")
    private String deliveryMode;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @Column(name = "requested_lead_days")
    private Integer requestedLeadDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_requested")
    private Moneda currencyRequested;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
