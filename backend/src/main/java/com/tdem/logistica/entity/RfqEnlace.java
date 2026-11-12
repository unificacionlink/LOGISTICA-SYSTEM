package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Enlace publico para que el proveedor cotice sobre su RFQ (DEC-L052). Solo guarda el hash del token. */
@Entity
@Table(name = "rfq_enlace")
@Getter
@Setter
@NoArgsConstructor
public class RfqEnlace {

    @Id
    @Column(name = "link_id")
    private UUID linkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
