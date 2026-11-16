package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Recepcion en destino de un envio (ALM-03, V38.7 opSaveDelivery). La NI firmada
 * va como archivo aparte; si no, el enlace de la conformidad firmada.
 *
 * @param clientToken uno por formulario abierto: el mismo envio no se registra dos veces.
 * @param resultado   CONFORME o NO_CONFORME (este exige observacion).
 */
public record RecepcionDestinoRequest(
        @NotNull UUID clientToken,
        @NotEmpty List<@Valid Linea> items,
        @NotBlank String resultado,
        @Size(max = 500) String observacion,
        @Size(max = 500) String enlaceConformidad) {

    public record Linea(
            @NotNull UUID issueItemId,
            @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 0) BigDecimal qty) {
    }
}
