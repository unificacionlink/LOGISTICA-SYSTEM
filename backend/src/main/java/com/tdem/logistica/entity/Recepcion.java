package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Recepcion de una OC (RF-030/032), solo cantidades aceptadas: en el almacen
 * central o, si la OC es de entrega directa, en un CECO de destino (DEC-L084).
 */
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
    @JoinColumn(name = "warehouse_id")
    private Almacen almacen;

    /** CECO donde el proveedor entrego (entrega directa); null si fue en el almacen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_ceco_id")
    private Ceco cecoDestino;

    /** Constancia de la entrega directa: enlace o archivo firmado. */
    @Column(name = "conformity_url")
    private String conformityUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Documento documento;

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
