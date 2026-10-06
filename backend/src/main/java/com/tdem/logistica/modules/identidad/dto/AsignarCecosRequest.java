package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** CECOs para los que el usuario puede pedir, de cualquier empresa (DEC-L029). Lista vacia = ninguno. */
public record AsignarCecosRequest(@NotNull(message = "Indica los CECOs.") List<UUID> cecoIds) {
}
