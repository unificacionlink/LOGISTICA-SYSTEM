package com.tdem.logistica.modules.presupuestos;

import java.time.YearMonth;

/** Mes 1..6 de un semestre presupuestal: S1 = enero a junio, S2 = julio a diciembre. */
final class MesesSemestre {

    private static final String[] NOMBRES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
            "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private MesesSemestre() {
    }

    static YearMonth mes(int anio, int semestre, int mes) {
        return YearMonth.of(anio, (semestre == 2 ? 6 : 0) + mes);
    }

    static String nombre(int semestre, int mes) {
        return NOMBRES[(semestre == 2 ? 6 : 0) + mes - 1];
    }
}
