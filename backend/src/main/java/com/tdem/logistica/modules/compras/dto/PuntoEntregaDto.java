package com.tdem.logistica.modules.compras.dto;

import java.util.UUID;

/** Direccion de entrega para la OC (DEC-L089). */
public record PuntoEntregaDto(UUID id, String nombre, String direccion, String tipo, String empresaCode,
                              String cecoCode, String cecoNombre) {
}
