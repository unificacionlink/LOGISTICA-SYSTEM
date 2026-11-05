package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Solo mapea las columnas que este backend necesita leer. Las columnas
 * restantes (product_type_id, subfamily_id, etc., NOT NULL en la BD) no se
 * mapean porque este servicio nunca crea productos nuevos — el catalogo de
 * 725 SKU ya esta sembrado (ver db_design_reference/).
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "sku_base", nullable = false, unique = true)
    private String skuBase;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_uom_id", nullable = false)
    private UnidadMedida baseUom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false)
    private FamiliaProducto familia;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Plazo de atencion en dias calendario; null = sin configurar (DEC-L035). */
    @Column(name = "attention_days")
    private Integer attentionDays;
}
