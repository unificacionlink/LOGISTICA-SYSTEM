package com.tdem.logistica.modules.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pestana Ventas del dashboard (DEC-L086): lo que TDEM entrego a proyectos de
 * GOYDEL y METRIN en el periodo, al precio de venta de cada empresa (PEN), para
 * facturar fuera del ERP. Cuenta lo despachado con Nota de Inventario (menos lo
 * devuelto al almacen) y lo que el proveedor entrego directo en el proyecto.
 */
public record VentasDto(
        List<Total> totales,
        List<String> meses,
        List<Monto> porMes,
        List<Monto> porCeco,
        List<Linea> lineas) {

    /** monto = solo lineas con precio; sinPrecio = lineas que no se pudieron valorizar. */
    public record Total(String empresa, BigDecimal monto, long lineas, long sinPrecio) {
    }

    /** clave = mes (YYYY-MM) o codigo del CECO; detalle = nombre del CECO. */
    public record Monto(String empresa, String clave, String detalle, BigDecimal monto) {
    }

    /** documento = Nota de Inventario o, en la entrega directa, la OC. precio null = sin precio de venta. */
    public record Linea(LocalDate fecha, String documento, String requerimiento, String empresa, String ceco,
                        String cecoNombre, String sku, String producto, BigDecimal cantidad, BigDecimal precio,
                        BigDecimal total) {
    }
}
