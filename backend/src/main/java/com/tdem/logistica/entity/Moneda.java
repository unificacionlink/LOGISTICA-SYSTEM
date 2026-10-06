package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "moneda")
@Getter
@Setter
@NoArgsConstructor
public class Moneda {

    @Id
    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;
}
