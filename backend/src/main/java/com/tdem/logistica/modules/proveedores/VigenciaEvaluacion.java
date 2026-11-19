package com.tdem.logistica.modules.proveedores;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Vigencia de la calificacion o de un documento (DEC-L070). Solo informa:
 * un vencimiento nunca bloquea compras ni deshabilita al proveedor (PD-10).
 */
public final class VigenciaEvaluacion {

    public static final String VIGENTE = "VIGENTE";
    public static final String PROXIMO_A_VENCER = "PROXIMO_A_VENCER";
    public static final String VENCIDO = "VENCIDO";
    public static final String SIN_EVALUACION = "SIN_EVALUACION";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private VigenciaEvaluacion() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(LIMA);
    }

    /** Sin fecha: SIN_EVALUACION. Pasada la fecha: VENCIDO. Dentro del aviso (incluido el dia): PROXIMO_A_VENCER. */
    public static String estado(LocalDate proxima, LocalDate hoy, int diasAviso) {
        if (proxima == null) {
            return SIN_EVALUACION;
        }
        if (hoy.isAfter(proxima)) {
            return VENCIDO;
        }
        return ChronoUnit.DAYS.between(hoy, proxima) <= diasAviso ? PROXIMO_A_VENCER : VIGENTE;
    }
}
