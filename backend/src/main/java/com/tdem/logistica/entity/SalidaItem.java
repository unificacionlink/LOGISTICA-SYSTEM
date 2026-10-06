package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Linea despachada; conserva request_item_id del requerimiento (RF-072). */
@Entity
@Table(name = "salida_item")
@Getter
@Setter
@NoArgsConstructor
public class SalidaItem {

    @Id
    @Column(name = "issue_item_id")
    private UUID issueItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Salida salida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @Column(name = "qty", nullable = false)
    private BigDecimal qty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id")
    private RequerimientoItem requerimientoItem;

    @Column(name = "purchase_cost_reference")
    private BigDecimal purchaseCostReference;
}
