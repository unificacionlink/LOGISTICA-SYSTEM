package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lo observado en destino se subsana o se devuelve al almacen central (ALM-03, DEC-L063). */
@Entity
@Table(name = "resolucion_destino")
@Getter
@Setter
@NoArgsConstructor
public class ResolucionDestino {

    @Id
    @Column(name = "resolution_id")
    private UUID resolutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Envio envio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_item_id", nullable = false)
    private SalidaItem salidaItem;

    @Column(name = "qty", nullable = false)
    private BigDecimal qty;

    /** SUBSANAR o DEVOLVER. */
    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "evidence_url", nullable = false)
    private String evidenceUrl;

    /** RESUELTA, EN_TRANSITO (devolucion en camino) o RECIBIDA_OBSERVADA (en cuarentena). */
    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private Usuario receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "return_evidence_url")
    private String returnEvidenceUrl;
}
