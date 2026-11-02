package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Nivel 1 del SKU (AC, EP, EQ, HE, MA, MU). En el catalogo V38 se llama
 * "Familia"; `familia_producto` es su "Subfamilia" (DEC-L034).
 */
@Entity
@Table(name = "tipo_producto")
@Getter
@Setter
@NoArgsConstructor
public class TipoProducto {

    @Id
    @Column(name = "product_type_id")
    private UUID productTypeId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;
}
