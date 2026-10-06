package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

public record CecoAdminDto(UUID id, String empresaCode, String code, String nombre, boolean activo) {
}
