package com.tdem.logistica.modules.requerimientos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemRequerimientoRequest(
        @NotNull(message = "Selecciona un producto valido.") UUID variantId,
        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "1", message = "La cantidad debe ser al menos 1.")
        @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.")
        BigDecimal qtyRequested,
        String technicalRequirement,
        String observation,
        /** DEC-L054: unidades que el solicitante sugiere comprar con la caja chica del proyecto. */
        @DecimalMin(value = "0", message = "Las unidades por caja chica no pueden ser negativas.")
        @Digits(integer = 6, fraction = 0, message = "Las unidades por caja chica deben ser un numero entero.")
        BigDecimal cantidadCajaChica) {

    public ItemRequerimientoRequest(UUID variantId, BigDecimal qtyRequested, String technicalRequirement,
                                    String observation) {
        this(variantId, qtyRequested, technicalRequirement, observation, null);
    }

    public BigDecimal cajaChica() {
        return cantidadCajaChica == null ? BigDecimal.ZERO : cantidadCajaChica;
    }
}
