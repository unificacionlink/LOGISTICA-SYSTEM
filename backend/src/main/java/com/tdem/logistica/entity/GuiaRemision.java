package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Guia de remision asociada a una recepcion (emisor: proveedor). */
@Entity
@Table(name = "guia_remision")
@Getter
@Setter
@NoArgsConstructor
public class GuiaRemision {

    @Id
    @Column(name = "guide_id")
    private UUID guideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrdenAbastecimiento orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Recepcion recepcion;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "issuer_type", nullable = false)
    private String issuerType;

    @Column(name = "issuer_ruc", nullable = false)
    private String issuerRuc;

    @Column(name = "series", nullable = false)
    private String series;

    @Column(name = "number", nullable = false)
    private String number;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Documento documento;
}
