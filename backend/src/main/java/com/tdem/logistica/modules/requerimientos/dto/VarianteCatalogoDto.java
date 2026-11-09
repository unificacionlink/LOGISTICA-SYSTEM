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
        String imagenUrl,
        /** Plazo de atencion del SKU en dias calendario; null = sin configurar (DEC-L035). */
        Integer plazoAtencionDias,
        /** Disponible en el almacen central (fisico - reservado); 0 si no hay. */
        java.math.BigDecimal stockDisponible) {
}
