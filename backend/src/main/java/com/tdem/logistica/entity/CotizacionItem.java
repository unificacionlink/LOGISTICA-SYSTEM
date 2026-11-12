package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cotizacion_item")
@Getter
@Setter
@NoArgsConstructor
public class CotizacionItem {

    @Id
    @Column(name = "quotation_item_id")
    private UUID quotationItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_item_id", nullable = false)
    private RfqItem rfqItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @Column(name = "qty", nullable = false)
    private BigDecimal qty;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    @Column(name = "offered_brand")
    private String offeredBrand;

    @Column(name = "offered_model")
    private String offeredModel;

    /** Codigo y descripcion que uso el proveedor para esta linea (DEC-L053). */
    @Column(name = "supplier_code")
    private String supplierCode;

    @Column(name = "supplier_description")
    private String supplierDescription;
}
