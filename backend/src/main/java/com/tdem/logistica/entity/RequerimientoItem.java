package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "requerimiento_item")
@Getter
@Setter
@NoArgsConstructor
public class RequerimientoItem {

    @Id
    @Column(name = "request_item_id")
    private UUID requestItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Requerimiento requerimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id", nullable = false)
    private UnidadMedida uom;

    @Column(name = "qty_requested", nullable = false)
    private BigDecimal qtyRequested;

    @Column(name = "technical_requirement")
    private String technicalRequirement;

    @Column(name = "observation")
    private String observation;
}
