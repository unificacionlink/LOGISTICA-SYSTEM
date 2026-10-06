package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Compra por Caja Chica (DEC-L026). Solo se mapea lo que usan las consultas
 * JPQL de pendiente de compra; la escritura la hace CajaChicaService con SQL.
 */
@Entity
@Table(name = "caja_chica_ref")
@Getter
@Setter
@NoArgsConstructor
public class CajaChicaRef {

    @Id
    @Column(name = "petty_cash_ref_id")
    private UUID pettyCashRefId;

    @Column(name = "petty_cash_code", nullable = false)
    private String pettyCashCode;

    @Column(name = "regularization_status", nullable = false)
    private String regularizationStatus;
}
