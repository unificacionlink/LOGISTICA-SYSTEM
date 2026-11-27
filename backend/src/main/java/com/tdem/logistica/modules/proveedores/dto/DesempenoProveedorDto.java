package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Desempeno calculado con lo que ya registran Compras y Almacen. Lo que no se
 * puede calcular con los datos actuales va en {@code pendientes}, nunca inventado.
 */
public record DesempenoProveedorDto(
        int ordenes,
        int ordenesConRecepcion,
        BigDecimal cantidadOrdenada,
        BigDecimal cantidadRecibida,
        BigDecimal atencionPct,
        int recepciones,
        int recepcionesObservadas,
        Incidencias incidencias,
        BigDecimal plazoRealPromedioDias,
        Plazo plazoOfrecido,
        int cotizaciones,
        int cotizacionesAdjudicadas,
        List<Pendiente> pendientes) {

    public record Incidencias(int total, int abiertas, int cerradas, BigDecimal diasCierrePromedio,
                              List<PorTipo> porTipo) {
    }

    public record PorTipo(String tipo, int cantidad) {
    }

    /**
     * Puntualidad con la primera recepcion (DEC-L103): contra la fecha comprometida de la OC
     * ({@code conFechaComprometida} de ellas) o, en las OC sin plazo estructurado, contra el
     * plazo de la cotizacion (referencial).
     */
    public record Plazo(int ordenesConPlazo, int dentroDelPlazo, int conFechaComprometida) {
    }

    public record Pendiente(String indicador, String motivo) {
    }
}
