package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calificacion de los formatos F-LOG-03 / F-LOG-07 (V38.7, DEC-L070):
 * nota = Σ peso% × puntaje / 100, redondeada a 2 decimales, en la escala de
 * la plantilla. El resultado es informativo: nunca cambia el estado del proveedor.
 */
public final class CalificacionProveedor {

    public static final String APROBADO = "APROBADO";
    public static final String NO_APROBADO = "NO_APROBADO";
    public static final String PENDIENTE_CONFORMIDAD = "PENDIENTE_CONFORMIDAD";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /** Peso (en %) y puntaje de un criterio. */
    public record Linea(BigDecimal peso, BigDecimal puntaje) {
    }

    private CalificacionProveedor() {
    }

    public static BigDecimal nota(List<Linea> lineas) {
        BigDecimal suma = BigDecimal.ZERO;
        for (Linea l : lineas) {
            suma = suma.add(l.peso().multiply(l.puntaje()));
        }
        return suma.divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    /** Los pesos deben sumar exactamente 100%. */
    public static void validarPesos(List<BigDecimal> pesos) {
        BigDecimal suma = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            throw ApiException.badRequest("Los pesos de los criterios deben sumar 100% (suman "
                    + suma.stripTrailingZeros().toPlainString() + "%).");
        }
    }

    /**
     * Bajo la nota aprobatoria: NO_APROBADO. Con nota suficiente pero sin las
     * dos conformidades (Logistica y area usuaria): PENDIENTE_CONFORMIDAD.
     */
    public static String resultado(BigDecimal nota, BigDecimal umbral, String conformidadLogistica,
                                   String conformidadArea) {
        if (nota.compareTo(umbral) < 0) {
            return NO_APROBADO;
        }
        if (!"SI".equals(conformidadLogistica) || !"SI".equals(conformidadArea)) {
            return PENDIENTE_CONFORMIDAD;
        }
        return APROBADO;
    }
}
