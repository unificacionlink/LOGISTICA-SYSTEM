package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Polimorfica (entity_type/entity_id, sin FK directa) — igual que el diseno SQL original. */
@Entity
@Table(name = "aprobacion")
@Getter
@Setter
@NoArgsConstructor
public class Aprobacion {

    @Id
    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "comment")
    private String comment;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
