package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "almacen")
@Getter
@Setter
@NoArgsConstructor
public class Almacen {

    @Id
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "warehouse_type", nullable = false)
    private String warehouseType;

    @Column(name = "active", nullable = false)
    private boolean active;
}
