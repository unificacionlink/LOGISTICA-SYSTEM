package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Faltante de un item de requerimiento tras reservar stock (RN-002, DEC-L039). */
@Entity
@Table(name = "necesidad_compra")
@Getter
@Setter
@NoArgsConstructor
public class NecesidadCompra {

    public static final String PENDIENTE_RUTA = "PENDIENTE_RUTA";
    public static final String COMPRA = "COMPRA";
    public static final String CAJA_CHICA = "CAJA_CHICA";
    public static final String ANULADA = "ANULADA";

    @Id
    @Column(name = "need_id")
    private UUID needId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequerimientoItem requerimientoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id", nullable = false)
    private UnidadMedida uom;

    @Column(name = "qty", nullable = false)
    private BigDecimal qty;

    @Column(name = "status", nullable = false)
    private String status;

    /** Motivo por el que la regla de Caja Chica sugiere esa ruta (null = compra normal). */
    @Column(name = "suggested_reason")
    private String suggestedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consolidation_id")
    private Consolidacion consolidacion;

    @Column(name = "route_decided_by")
    private UUID routeDecidedBy;

    @Column(name = "route_decided_at")
    private Instant routeDecidedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
