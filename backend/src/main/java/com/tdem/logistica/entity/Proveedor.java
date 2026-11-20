package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "proveedor")
@Getter
@Setter
@NoArgsConstructor
public class Proveedor {

    @Id
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "ruc", nullable = false, unique = true)
    private String ruc;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "email")
    private String email;

    @Column(name = "whatsapp_phone")
    private String whatsappPhone;

    @Column(name = "supplier_type", nullable = false)
    private String supplierType;

    @Column(name = "preferred_delivery_mode")
    private String preferredDeliveryMode;

    @Column(name = "active", nullable = false)
    private boolean active;

    // Ficha y ciclo de incorporacion (DEC-L069).
    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "phone")
    private String phone;

    @Column(name = "website")
    private String website;

    @Column(name = "contact_role")
    private String contactRole;

    @Column(name = "offering_description")
    private String offeringDescription;

    @Column(name = "payment_condition")
    private String paymentCondition;

    @Column(name = "credit_days")
    private Integer creditDays;

    @Column(name = "lead_days")
    private Integer leadDays;

    @Column(name = "warranty_terms")
    private String warrantyTerms;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Empresa en la que se registro (DEC-L081): solo clasifica, no restringe compras. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Empresa empresa;
}
