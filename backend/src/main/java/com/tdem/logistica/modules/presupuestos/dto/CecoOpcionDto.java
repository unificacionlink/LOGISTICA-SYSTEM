package com.tdem.logistica.modules.presupuestos.dto;

import java.util.UUID;

public record CecoOpcionDto(UUID cecoId, String empresa, String code, String nombre) {
}
