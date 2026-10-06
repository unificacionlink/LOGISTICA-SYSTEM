package com.tdem.logistica.modules.maestro.dto;

import java.time.Instant;
import java.util.UUID;

public record SolicitudProductoDto(
        UUID id,
        String codigo,
        String estado,
        String empresaCode,
        String solicitante,
        Instant solicitadaEn,
        UUID familyId,
        String familia,
        String nombre,
        UUID uomId,
        String unidad,
        String atributoTipo,
        String atributoValor,
        String descripcionTecnica,
        String justificacion,
        String posibleDuplicado,
        String comentarioRevision,
        String skuGenerado) {
}
