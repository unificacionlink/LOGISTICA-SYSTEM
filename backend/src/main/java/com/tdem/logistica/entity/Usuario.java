package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Identidad/autorizacion del ERP (login, rol, CECO visible) — NO es
 * mantenedor de personal (ver docs/04_DECISIONES.md, DEC-L002).
 * `passwordHash` reemplaza a Supabase Auth (DEC-L008): Spring Security
 * gestiona credenciales directo en esta tabla.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Empresa empresa;

    @Column(name = "employee_external_id")
    private String employeeExternalId;

    @Column(name = "corporate_email", nullable = false, unique = true)
    private String corporateEmail;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "fuente", nullable = false)
    private String fuente;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "cargo")
    private String cargo;

    @Column(name = "source_synced_at")
    private Instant sourceSyncedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
