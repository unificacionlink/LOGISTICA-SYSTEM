package com.tdem.logistica.modules.requerimientos.dto;

import java.util.UUID;

public record CecoDto(UUID cecoId, String code, String name, String empresaCode) {
}
