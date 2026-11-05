package com.tdem.logistica.modules.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Historial de un SKU: saldo actual y movimientos, del mas reciente al mas antiguo. */
public record KardexDto(SaldoStockDto saldo, List<Movimiento> movimientos) {

    /**
     * tipo: ENTRADA, SALIDA o AJUSTE. origen: RECEPCION, SALIDA, CAJA_CHICA,
     * AJUSTE o CARGA_STOCK. referencia: codigo del documento (OC y recepcion,
     * NI, CC, INV-n). saldo: fisico despues del movimiento.
     */
    public record Movimiento(Instant fecha, String tipo, String origen, BigDecimal cantidad, BigDecimal saldo,
                             String referencia, String empresa, String motivo, String usuario) {
    }
}
