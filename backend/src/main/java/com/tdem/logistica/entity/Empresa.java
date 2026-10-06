package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "active", nullable = false)
    private boolean active;
}
