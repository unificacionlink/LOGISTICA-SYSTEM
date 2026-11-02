package com.tdem.logistica.modules.requerimientos.dto;

import java.util.UUID;

/**
 * Subfamilia del catalogo (`familia_producto`) con su familia (`tipo_producto`),
 * con los nombres del maestro SKU V38 (DEC-L034).
 */
public record FamiliaDto(
        UUID familyId, String code, String nombre, UUID tipoId, String tipoCode, String tipoNombre) {
}
