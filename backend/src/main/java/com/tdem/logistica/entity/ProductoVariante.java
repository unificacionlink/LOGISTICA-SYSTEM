package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "producto_variante")
@Getter
@Setter
@NoArgsConstructor
public class ProductoVariante {

    @Id
    @Column(name = "variant_id")
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @Column(name = "attribute_type")
    private String attributeType;

    @Column(name = "attribute_value")
    private String attributeValue;

    @Column(name = "internal_variant_code", nullable = false, unique = true)
    private String internalVariantCode;

    @Column(name = "active", nullable = false)
    private boolean active;
}
