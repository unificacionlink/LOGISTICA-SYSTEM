package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Solicitud de cotizacion a un proveedor por empresa, con varios productos
 * consolidados (DEC-L039). request_id solo existe en las RFQ previas al Lote 16
 * (una por requerimiento). BORRADOR = preparada, por enviar.
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
    @JoinColumn(name = "request_id")
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
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "sent_by")
    private UUID sentBy;
    @Column(name = "sent_channel")
    private String sentChannel;
}
