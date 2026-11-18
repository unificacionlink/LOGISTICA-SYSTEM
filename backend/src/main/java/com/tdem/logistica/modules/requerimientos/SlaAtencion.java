package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.common.AppConstants.PrioridadRequerimiento;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Set;

/**
 * Tiempo de atencion por prioridad (DEC-L068): Urgente 2, Aprovisionamiento 10
 * e Implementacion 20 dias habiles. Habil = lunes a viernes que no sea feriado
 * nacional de Peru. La fecha tentativa la calcula el sistema; el solicitante no
 * la escribe.
 */
public final class SlaAtencion {

    /** Plazo de una prioridad, en el orden en que se muestran. */
    public record Plazo(String prioridad, String etiqueta, int diasHabiles) {
    }

    public static final List<Plazo> PLAZOS = List.of(
            new Plazo(PrioridadRequerimiento.URGENTE, "Urgente", 2),
            new Plazo(PrioridadRequerimiento.APROVISIONAMIENTO, "Aprovisionamiento", 10),
            new Plazo(PrioridadRequerimiento.IMPLEMENTACION, "Implementación", 20));

    /** Feriados nacionales de fecha fija (Ley 31788 y anteriores). */
    private static final Set<MonthDay> FERIADOS_FIJOS = Set.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(6, 7), MonthDay.of(6, 29),
            MonthDay.of(7, 23), MonthDay.of(7, 28), MonthDay.of(7, 29), MonthDay.of(8, 6),
            MonthDay.of(8, 30), MonthDay.of(10, 8), MonthDay.of(11, 1), MonthDay.of(12, 8),
            MonthDay.of(12, 9), MonthDay.of(12, 25));

    private SlaAtencion() {
    }

    /** La prioridad recibida si es valida; si no, la de por defecto. */
    public static String normalizar(String prioridad) {
        return prioridad != null && PrioridadRequerimiento.VALORES.contains(prioridad)
                ? prioridad
                : PrioridadRequerimiento.POR_DEFECTO;
    }

    public static Plazo plazo(String prioridad) {
        String p = normalizar(prioridad);
        return PLAZOS.stream().filter(x -> x.prioridad().equals(p)).findFirst().orElseThrow();
    }

    public static String etiqueta(String prioridad) {
        return PLAZOS.stream().filter(x -> x.prioridad().equals(prioridad)).map(Plazo::etiqueta)
                .findFirst().orElse(prioridad == null ? "" : prioridad);
    }

    /** Fecha tentativa de atencion: {@code desde} + los dias habiles de la prioridad. */
    public static LocalDate fechaTentativa(String prioridad, LocalDate desde) {
        return sumarHabiles(desde, plazo(prioridad).diasHabiles());
    }

    static LocalDate sumarHabiles(LocalDate desde, int dias) {
        LocalDate d = desde;
        int contados = 0;
        while (contados < dias) {
            d = d.plusDays(1);
            if (esHabil(d)) {
                contados++;
            }
        }
        return d;
    }

    /** Dias habiles despues de {@code desde} hasta {@code hasta} inclusive (0 si no avanza). */
    public static int habilesEntre(LocalDate desde, LocalDate hasta) {
        int n = 0;
        for (LocalDate d = desde.plusDays(1); !d.isAfter(hasta); d = d.plusDays(1)) {
            if (esHabil(d)) {
                n++;
            }
        }
        return n;
    }

    public static boolean esHabil(LocalDate d) {
        DayOfWeek dia = d.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !esFeriado(d);
    }

    static boolean esFeriado(LocalDate d) {
        if (FERIADOS_FIJOS.contains(MonthDay.from(d))) {
            return true;
        }
        LocalDate pascua = domingoDePascua(d.getYear());
        // Jueves y Viernes Santo.
        return d.equals(pascua.minusDays(3)) || d.equals(pascua.minusDays(2));
    }

    /** Algoritmo de Meeus/Jones/Butcher (calendario gregoriano). */
    static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(anio, mes, dia);
    }
}
