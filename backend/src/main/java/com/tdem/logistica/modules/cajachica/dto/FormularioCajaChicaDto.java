package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Compra con la caja chica del proyecto de un requerimiento (DEC-L054): lo que
 * Logistica autorizo comprar y aun falta registrar, y las compras ya hechas.
 */
public record FormularioCajaChicaDto(
        UUID requestId,
        String requestCode,
        String empresaCode,
        String cecoNombre,
        String requestStatus,
        boolean puedeRegistrar,
        List<Item> items,
        List<CajaChicaDto> compras) {

    /** {@code autorizado}: lo que Logistica paso a caja chica; {@code pendiente}: lo que falta registrar. */
    public record Item(UUID requestItemId, String sku, String producto, String atributo, String unidad,
                       BigDecimal autorizado, BigDecimal pendiente) {
    }
}
