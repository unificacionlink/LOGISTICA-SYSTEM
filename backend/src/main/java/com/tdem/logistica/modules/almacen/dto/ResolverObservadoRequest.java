package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Resolver lo observado de un producto (V38.7 aiResolve): SUBSANAR o DEVOLVER, con enlace de evidencia. */
public record ResolverObservadoRequest(
        @NotNull UUID issueItemId,
        @NotNull @Positive @Digits(integer = 11, fraction = 0) BigDecimal qty,
        @NotBlank String accion,
        @NotBlank @Size(max = 500) String evidencia) {
}
