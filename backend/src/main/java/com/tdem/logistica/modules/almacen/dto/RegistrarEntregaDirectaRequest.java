package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Recepcion de una entrega directa en un CECO (DEC-L084). La guia del proveedor
 * es obligatoria; la constancia firmada va como archivo aparte o como enlace.
 * Solo cuenta lo aceptado: lo que llega mal se registra como incidencia y queda
 * pendiente para que el proveedor lo reponga.
 */
public record RegistrarEntregaDirectaRequest(
        @NotNull(message = "Indica el CECO que recibe.") UUID cecoId,
        @NotNull(message = "Registra la guía de remisión del proveedor.") GuiaInput guia,
        @Size(max = 500, message = "El enlace admite hasta 500 caracteres.") String enlaceConformidad,
        @Size(max = 1000, message = "La observación admite hasta 1000 caracteres.") String observation,
        @NotEmpty(message = "Indica lo recibido.") List<@Valid Linea> lineas) {

    public record Linea(
            @NotNull UUID orderItemId,
            UUID needId,
            UUID requestItemId,
            @NotNull @PositiveOrZero
            @Digits(integer = 11, fraction = 0, message = "La cantidad debe ser un número entero.") BigDecimal qty,
            @Valid IncidenciaInput incidencia) {
    }
}
