package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

/** {@code aprobadorId}: aprobador asignado del CECO (DEC-L030), o null. */
public record CecoAdminDto(UUID id, String empresaCode, String code, String nombre, boolean activo, UUID aprobadorId) {
}
