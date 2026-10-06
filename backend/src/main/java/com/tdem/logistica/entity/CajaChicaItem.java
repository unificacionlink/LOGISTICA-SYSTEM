package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Linea de una compra por Caja Chica, vinculada al item del REQ (DEC-L026). */
@Entity
@Table(name = "caja_chica_item")
@Getter
@Setter
@NoArgsConstructor
public class CajaChicaItem {

    @Id
    @Column(name = "petty_cash_item_id")
    private UUID pettyCashItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "petty_cash_ref_id", nullable = false)
    private CajaChicaRef ref;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequerimientoItem requerimientoItem;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;
}
