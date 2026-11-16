package com.tdem.logistica.modules.almacen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Enlace de evidencia (V38.7): retorno fisico recibido en el almacen central. */
public record EvidenciaRequest(@NotBlank @Size(max = 500) String evidencia) {
}
