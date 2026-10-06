package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Salida de inventario; en este lote siempre viene de un requerimiento. */
@Entity
@Table(name = "salida")
@Getter
@Setter
@NoArgsConstructor
public class Salida {

    @Id
    @Column(name = "issue_id")
    private UUID issueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Almacen almacen;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceco_id")
    private Ceco ceco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private Requerimiento requerimiento;

    @Column(name = "requires_invoice", nullable = false)
    private boolean requiresInvoice;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;
}
