package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Una recepcion (parcial o total) de un envio en destino (ALM-03, DEC-L063). */
@Entity
@Table(name = "recepcion_destino")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionDestino {

    @Id
    @Column(name = "destination_receipt_id")
    private UUID destinationReceiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Envio envio;

    @Column(name = "receipt_number", nullable = false)
    private Integer receiptNumber;

    @Column(name = "client_token", nullable = false)
    private UUID clientToken;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "observation")
    private String observation;

    @Column(name = "conformity_url")
    private String conformityUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Documento documento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by", nullable = false)
    private Usuario receivedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
