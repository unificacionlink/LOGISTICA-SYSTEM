package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Imagen de producto (variant_id null = aplica a todas las variantes del
 * SKU base, ver docs_base 16_Producto_Imagen_V32). Solo una principal activa
 * por producto+variante (indice parcial en V1__baseline_lote1.sql).
 */
@Entity
@Table(name = "producto_imagen")
@Getter
@Setter
@NoArgsConstructor
public class ProductoImagen {

    @Id
    @Column(name = "product_image_id")
    private UUID productImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @Column(name = "variant_id")
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Documento documento;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
