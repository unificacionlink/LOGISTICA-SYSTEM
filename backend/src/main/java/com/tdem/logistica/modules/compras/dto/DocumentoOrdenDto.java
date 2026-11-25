package com.tdem.logistica.modules.compras.dto;

import java.time.Instant;

/**
 * Documento relacionado con una OC (DEC-L089): constancias y factura del pago,
 * guias y recepciones del proveedor y Notas de Inventario de sus requerimientos.
 * {@code url} = archivo para abrir; {@code ruta} = pantalla del ERP; ambos opcionales.
 */
public record DocumentoOrdenDto(String tipo, String titulo, String detalle, Instant fecha, String url, String ruta) {
}
