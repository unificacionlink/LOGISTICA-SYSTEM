package com.tdem.logistica.modules.proveedores.dto;

import java.time.Instant;
import java.util.UUID;

/** Cuenta bancaria del proveedor (DEC-L078). Solo la reciben Logistica y Admin. */
public record CuentaBancariaDto(
        UUID id,
        String banco,
        String tipo,
        String moneda,
        String numero,
        String cci,
        boolean principal,
        Instant registradaEn) {
}
