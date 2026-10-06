package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Nota de Inventario (RF-073/074): varias por requerimiento; el detalle es la salida_item de su salida. */
@Entity
@Table(name = "nota_inventario")
@Getter
@Setter
@NoArgsConstructor
public class NotaInventario {

    @Id
    @Column(name = "inventory_note_id")
    private UUID inventoryNoteId;

    @Column(name = "note_number", nullable = false)
    private Integer noteNumber;

    @Column(name = "inventory_note_code", nullable = false)
    private String inventoryNoteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Requerimiento requerimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Salida salida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_warehouse_id", nullable = false)
    private Almacen almacenOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_ceco_id")
    private Ceco cecoDestino;

    @Column(name = "delivery_point_id")
    private UUID deliveryPointId;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "picking_date", nullable = false)
    private LocalDate pickingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "picking_user_id", nullable = false)
    private Usuario pickingUser;

    @Column(name = "item_state", nullable = false)
    private String itemState;

    @Column(name = "total_value_snapshot")
    private BigDecimal totalValueSnapshot;

    @Column(name = "total_item_qty")
    private BigDecimal totalItemQty;

    @Column(name = "package_count")
    private Integer packageCount;

    @Column(name = "comments")
    private String comments;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
