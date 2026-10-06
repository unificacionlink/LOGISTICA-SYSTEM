package com.tdem.logistica.modules.maestro.dto;

/** Atributo de una variante: TALLA, COLOR, MODELO u OTRO, y su valor (p. ej. TALLA / L). */
public record VarianteInput(String tipo, String valor) {
}
