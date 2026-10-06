package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * product_id/variant_id/uom_id se copian del requerimiento_item al crear la
 * RFQ (snapshot), mismo patron que requerimiento_item.unit_value_snapshot.
 */
@Entity
@Table(name = "rfq_item")
@Getter
@Setter
@NoArgsConstructor
public class RfqItem {

    @Id
    @Column(name = "rfq_item_id")
    private UUID rfqItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequerimientoItem requerimientoItem;

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

    @Column(name = "technical_specification")
    private String technicalSpecification;
}
