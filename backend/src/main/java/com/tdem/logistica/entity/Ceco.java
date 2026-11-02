package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "ceco")
@Getter
@Setter
@NoArgsConstructor
public class Ceco {

    @Id
    @Column(name = "ceco_id")
    private UUID cecoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Aprobador asignado (DEC-L030); null = cualquier APROBADOR/GERENCIA. Se escribe desde AdminService. */
    @Column(name = "approver_user_id", insertable = false, updatable = false)
    private UUID approverUserId;
}
