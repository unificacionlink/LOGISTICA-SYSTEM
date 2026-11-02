package com.tdem.logistica.modules.requerimientos.dto;

import java.util.UUID;

public record VarianteCatalogoDto(
        UUID variantId,
        UUID productId,
        String skuBase,
        String nombre,
        String atributo,
        String codigoVariante,
        String etiqueta,
        UUID familiaId,
        String familiaNombre,
        UUID tipoId,
        String tipoNombre,
        String imagenUrl) {
}
