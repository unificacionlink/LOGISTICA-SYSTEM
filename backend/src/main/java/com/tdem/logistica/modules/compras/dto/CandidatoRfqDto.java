package com.tdem.logistica.modules.compras.dto;

import java.util.List;
import java.util.UUID;

/**
 * Proveedor al que se le puede pedir cotizacion de los consolidados elegidos
 * (DEC-L082): {@code consolidados} son los que cubre.
 */
public record CandidatoRfqDto(
        UUID supplierId,
        String nombre,
        String ruc,
        boolean critico,
        String empresaCode,
        List<UUID> consolidados) {
}
