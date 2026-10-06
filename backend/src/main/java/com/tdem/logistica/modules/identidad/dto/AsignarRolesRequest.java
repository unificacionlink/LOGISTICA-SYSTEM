package com.tdem.logistica.modules.identidad.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AsignarRolesRequest(@NotNull(message = "Indica los roles.") List<String> roles) {
}
