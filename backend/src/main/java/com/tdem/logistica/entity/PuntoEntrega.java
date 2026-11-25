package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Direccion de entrega de las OC (DEC-L089): el almacen central y los proyectos (CECO). */
@Entity
@Table(name = "punto_entrega")
@Getter
@Setter
@NoArgsConstructor
public class PuntoEntrega {

    @Id
    @Column(name = "delivery_point_id")
    private UUID deliveryPointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceco_id")
    private Ceco ceco;

    /** CECO, ALMACEN, AGENCIA, OFICINA, DOMICILIO u OTRO. */
    @Column(name = "point_type", nullable = false)
    private String pointType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;
}
