package com.tdem.logistica.modules.presupuestos.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LineaRequest(@NotNull UUID productId,
                           @NotNull @Size(min = 6, max = 6)
                           List<@NotNull @PositiveOrZero @Digits(integer = 6, fraction = 0) BigDecimal> meses) {
}
