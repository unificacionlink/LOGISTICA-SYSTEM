package com.tdem.logistica.modules.requerimientos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CrearRequerimientoRequest(
        @NotNull(message = "Selecciona un centro de costo valido.") UUID cecoId,
        String priority,
        @NotNull(message = "Selecciona un proposito.") String purpose,
        @NotNull(message = "La justificacion es obligatoria.")
        @Size(min = 10, max = 2000, message = "La justificacion debe tener al menos 10 caracteres.")
        String justification,
        LocalDate requiredDate,
        @NotEmpty(message = "Agrega al menos un item.")
        @Valid
        List<ItemRequerimientoRequest> items,
        /** DEC-L054: sugerir comprar el faltante con la caja chica del proyecto. */
        Boolean cajaChicaSugerida,
        @Size(max = 300, message = "La nota de caja chica admite hasta 300 caracteres.")
        String cajaChicaNota) {

    public CrearRequerimientoRequest(UUID cecoId, String priority, String purpose, String justification,
                                     LocalDate requiredDate, List<ItemRequerimientoRequest> items) {
        this(cecoId, priority, purpose, justification, requiredDate, items, null, null);
    }

    private boolean algunoConCajaChica() {
        return items != null && items.stream().anyMatch(i -> i.cajaChica().signum() > 0);
    }

    /** Algun producto con unidades por caja chica (o, de un cliente anterior, la casilla general). */
    public boolean sugiereCajaChica() {
        return Boolean.TRUE.equals(cajaChicaSugerida) || algunoConCajaChica();
    }

    /**
     * Unidades del producto por caja chica: las indicadas; si no se indico
     * ninguna pero vino la casilla general (cliente anterior), todas.
     */
    public java.math.BigDecimal cantidadCajaChica(ItemRequerimientoRequest item) {
        if (algunoConCajaChica()) {
            return item.cajaChica();
        }
        return Boolean.TRUE.equals(cajaChicaSugerida) ? item.qtyRequested() : java.math.BigDecimal.ZERO;
    }

    public String notaCajaChica() {
        return sugiereCajaChica() && cajaChicaNota != null && !cajaChicaNota.isBlank() ? cajaChicaNota.trim() : null;
    }
}
