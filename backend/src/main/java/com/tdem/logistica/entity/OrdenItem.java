package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linea de OC por consolidado (DEC-L039): lo recibido se reparte entre las
 * necesidades de sus requerimientos. request_item_id solo en OC previas al
 * Lote 16.
 */
@Entity
@Table(name = "orden_item")
@Getter
@Setter
@NoArgsConstructor
public class OrdenItem {

    @Id
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrdenAbastecimiento orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id")
    private RequerimientoItem requerimientoItem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consolidation_id")
    private Consolidacion consolidacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @Column(name = "description", nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id")
    private UnidadMedida uom;

    @Column(name = "qty_ordered", nullable = false)
    private BigDecimal qtyOrdered;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "line_subtotal", nullable = false)
    private BigDecimal lineSubtotal;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
