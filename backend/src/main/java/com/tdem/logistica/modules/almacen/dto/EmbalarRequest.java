package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Embalaje de la NI (V38.7 aiPackingForm): bultos y enlace de la evidencia. */
public record EmbalarRequest(@NotNull Integer bultos, @NotBlank @Size(max = 500) String evidencia) {
}
