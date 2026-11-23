package com.tdem.logistica.modules.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pestana Demanda del dashboard (dashboard operativo de la V38.7, DEC-L083):
 * que se pide, cuanto y de que familia, por lineas de requerimiento enviadas
 * en el periodo, mas la matriz ABC de lo despachado. Una sola consulta JSON.
 */
public record DemandaDto(
        long requerimientos,
        long lineas,
        long skus,
        long familias,
        List<Grupo> porFamilia,
        List<Grupo> porMes,
        List<Producto> productos,
        List<Grupo> porEstado,
        Abc abc,
        long totalDetalle,
        List<Linea> detalle) {

    /** Barra: etiqueta, lineas de requerimiento y unidades pedidas. */
    public record Grupo(String clave, long lineas, BigDecimal unidades) {
    }

    public record Producto(String sku, String nombre, long lineas, BigDecimal unidades) {
    }

    /**
     * Matriz ABC de lo despachado en el periodo, valorizado al ultimo costo neto de
     * compra en soles de cada producto. A = hasta el 80% del valor, B = hasta el 95%,
     * C = el resto. sinCosto = productos despachados que nunca se compraron en soles.
     */
    public record Abc(BigDecimal total, List<Clase> clases, List<ProductoAbc> productos, long sinCosto) {
    }

    public record Clase(String clase, long productos, BigDecimal valor) {
    }

    public record ProductoAbc(String sku, String nombre, BigDecimal unidades, BigDecimal valor, BigDecimal pct,
                              String clase) {
    }

    /** Fila del detalle (mismas columnas que el Excel). */
    public record Linea(String requerimiento, String empresa, String ceco, LocalDate fecha, String estado,
                       String familia, String subfamilia, String sku, String producto, BigDecimal cantidad) {
    }
}
