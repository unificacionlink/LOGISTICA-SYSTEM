package com.tdem.logistica.modules.presupuestos.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SKU del REQ fuera de presupuesto. motivo = SIN_PRESUPUESTO_APROBADO | SKU_NO_PRESUPUESTADO | EXCESO. */
public record DesviacionDto(UUID productId, String sku, BigDecimal solicitado, BigDecimal disponible,
                            BigDecimal exceso, String motivo) {
}
