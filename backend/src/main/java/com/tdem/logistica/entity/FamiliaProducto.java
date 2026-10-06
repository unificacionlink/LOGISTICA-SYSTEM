package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "familia_producto")
@Getter
@Setter
@NoArgsConstructor
public class FamiliaProducto {

    @Id
    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;
}
