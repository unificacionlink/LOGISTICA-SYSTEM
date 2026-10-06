package com.tdem.logistica.modules.identidad.dto;

import java.util.UUID;

/** {@code altaManual}: RRHH aun no cubre la empresa, se permite alta manual (DEC-L002). */
public record EmpresaAdminDto(UUID id, String code, String nombre, boolean altaManual) {
}
