package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** F-LOG-08 (DEC-L102): una fila por linea de la OC; Y = conforme, F = no conforme, vacio = no aplica. */
public record GuardarVerificacionRequest(
        @NotNull(message = "Elige quién da el V°B° del área solicitante o especialista.") UUID especialistaUserId,
        @NotBlank(message = "Escribe las observaciones o conclusiones de la verificación.")
        @Size(max = 2000, message = "Las conclusiones no pasan de 2000 caracteres.") String conclusion,
        @NotEmpty(message = "No hay productos que verificar.") @Valid List<Item> items) {

    public record Item(
            @NotNull(message = "Falta la línea de la orden.") UUID orderItemId,
            @Pattern(regexp = "Y|F", message = "Funcionalidad: Y o F.") String funcionalidad,
            @Pattern(regexp = "Y|F", message = "Estándares: Y o F.") String estandares,
            @NotNull(message = "Marca si cada producto cumple con las especificaciones (Y o F).")
            @Pattern(regexp = "Y|F", message = "Especificaciones: Y o F.") String especificaciones,
            @Size(max = 500, message = "La observación no pasa de 500 caracteres.") String observacion) {
    }
}
