package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "unidad_medida")
@Getter
@Setter
@NoArgsConstructor
public class UnidadMedida {

    @Id
    @Column(name = "uom_id")
    private UUID uomId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;
}
