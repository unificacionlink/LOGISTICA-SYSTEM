package com.tdem.logistica.modules.tracking.dto;

import java.time.Instant;

/** RF-083: hito calculado desde eventos y entregas (nunca un checkbox manual). */
public record HitoDto(String codigo, String etiqueta, boolean cumplido, Instant fecha, String detalle) {
}
