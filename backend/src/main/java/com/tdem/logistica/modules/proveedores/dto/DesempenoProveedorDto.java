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

    /** Referencial: liberacion (o aprobacion) + plazo ofrecido en la cotizacion contra la primera recepcion. */
    public record Plazo(int ordenesConPlazo, int dentroDelPlazo) {
    }

    public record Pendiente(String indicador, String motivo) {
    }
}
