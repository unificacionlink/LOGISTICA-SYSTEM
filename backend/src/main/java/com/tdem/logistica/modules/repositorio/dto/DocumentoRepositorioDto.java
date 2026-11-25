package com.tdem.logistica.modules.repositorio.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un documento del repositorio (DEC-L091). tipo: REQUERIMIENTO, ORDEN_COMPRA,
 * NOTA_INVENTARIO (PDF archivados por el ERP), CONSTANCIA_PAGO, FACTURA, GUIA,
 * COMPROBANTE_CAJA_CHICA o DOC_PROVEEDOR (archivos subidos). descarga = ruta de
 * la API que lo entrega (bucket privado); url = archivo publico; ruta = pantalla
 * del ERP. vence solo en documentos de proveedor.
 */
public record DocumentoRepositorioDto(String tipo, String codigo, String detalle, String empresaCode, String periodo,
                                      Instant fecha, LocalDate vence, String descarga, String url, String ruta) {
}
