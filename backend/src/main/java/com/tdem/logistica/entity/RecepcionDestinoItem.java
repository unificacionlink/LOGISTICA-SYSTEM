package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recepcion_destino_item")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionDestinoItem {

    @Id
    @Column(name = "destination_receipt_item_id")
    private UUID destinationReceiptItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_receipt_id", nullable = false)
    private RecepcionDestino recepcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_item_id", nullable = false)
    private SalidaItem salidaItem;

    @Column(name = "qty", nullable = false)
    private BigDecimal qty;
}
