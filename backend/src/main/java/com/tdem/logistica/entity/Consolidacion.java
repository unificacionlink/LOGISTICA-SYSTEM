package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Necesidades de compra de varios requerimientos agrupadas por empresa + SKU +
 * unidad (RN-004, DEC-L039). Se cotiza y se ordena el total.
 */
@Entity
@Table(name = "consolidacion")
@Getter
@Setter
@NoArgsConstructor
public class Consolidacion {

    public static final String ABIERTA = "ABIERTA";
    public static final String EN_COTIZACION = "EN_COTIZACION";
    public static final String ADJUDICADA = "ADJUDICADA";
    public static final String ORDENADA = "ORDENADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @Column(name = "consolidation_id")
    private UUID consolidationId;

    @Column(name = "consolidation_code", nullable = false, unique = true)
    private String consolidationCode;

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

    @Column(name = "total_qty", nullable = false)
    private BigDecimal totalQty;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awarded_quotation_item_id")
    private CotizacionItem adjudicado;

    @Column(name = "awarded_by")
    private UUID awardedBy;

    @Column(name = "awarded_at")
    private Instant awardedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
