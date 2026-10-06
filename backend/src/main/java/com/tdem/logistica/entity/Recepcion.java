package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Recepcion de una OC en almacen (RF-030/032). Solo cantidades aceptadas. */
@Entity
@Table(name = "recepcion")
@Getter
@Setter
@NoArgsConstructor
public class Recepcion {

    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrdenAbastecimiento orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Almacen almacen;

    @Column(name = "receipt_number", nullable = false)
    private Integer receiptNumber;

    @Column(name = "receipt_date", nullable = false)
    private Instant receiptDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "registration_source", nullable = false)
    private String registrationSource;

    @Column(name = "observation")
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by", nullable = false)
    private Usuario receivedBy;
}
