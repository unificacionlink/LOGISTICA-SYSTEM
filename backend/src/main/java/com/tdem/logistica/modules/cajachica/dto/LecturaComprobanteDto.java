package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que el OCR pudo leer del comprobante de caja chica (DEC-L095). Cada campo
 * es null si no se reconocio con seguridad; nada se guarda: solo pre-llena el
 * formulario para que el usuario revise.
 *
 * @param tipo FACTURA o BOLETA
 */
public record LecturaComprobanteDto(
        String tipo,
        String serie,
        String numero,
        String ruc,
        String proveedor,
        LocalDate fecha,
        BigDecimal total,
        List<Linea> lineas) {

    public record Linea(String descripcion, BigDecimal cantidad, BigDecimal precioUnitario) {
    }

    public static LecturaComprobanteDto vacia() {
        return new LecturaComprobanteDto(null, null, null, null, null, null, null, List.of());
    }
}
