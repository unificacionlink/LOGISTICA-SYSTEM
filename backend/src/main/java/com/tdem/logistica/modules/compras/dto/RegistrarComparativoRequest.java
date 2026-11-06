package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Una cotizacion cargada en el comparativo: proveedor, condiciones y precio por consolidado. */
public record RegistrarComparativoRequest(
        @NotNull(message = "Selecciona el proveedor.") UUID supplierId,
        @NotEmpty(message = "Faltan los productos del comparativo.") @Size(max = 40) List<UUID> consolidationIds,
        String supplierQuoteNumber,
        LocalDate issueDate,
        LocalDate expirationDate,
        @NotNull(message = "Selecciona una moneda.") String currencyCode,
        @NotBlank(message = "La condicion de pago es obligatoria.") String paymentCondition,
        @NotBlank(message = "La modalidad de entrega es obligatoria.") String deliveryMode,
        Integer leadDays,
        UUID documentId,
        /** Cotizacion anterior del mismo proveedor que esta reemplaza (se descarta). */
        UUID reemplazaQuotationId,
        boolean leidaPorOcr,
        @NotEmpty(message = "Ingresa al menos un precio.") @Valid List<Precio> items) {

    public record Precio(
            @NotNull(message = "Falta el producto cotizado.") UUID consolidationId,
            @NotNull(message = "El costo unitario es obligatorio.")
            @DecimalMin(value = "0.0001", message = "El costo unitario debe ser mayor a 0.")
            BigDecimal unitCost,
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0.") BigDecimal qty,
            String offeredBrand,
            String offeredModel) {
    }
}
