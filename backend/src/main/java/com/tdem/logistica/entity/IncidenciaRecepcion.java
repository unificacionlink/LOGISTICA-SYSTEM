package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Faltante, dano o producto errado detectado al recibir (RF-033). */
@Entity
@Table(name = "incidencia_recepcion")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaRecepcion {

    @Id
    @Column(name = "receipt_issue_id")
    private UUID receiptIssueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Recepcion recepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrdenItem ordenItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_item_id")
    private RecepcionItem recepcionItem;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(name = "qty_affected")
    private BigDecimal qtyAffected;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "supplier_action")
    private String supplierAction;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "closed_at")
    private Instant closedAt;
}
