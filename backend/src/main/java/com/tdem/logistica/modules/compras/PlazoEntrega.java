package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.OrdenAbastecimiento;
import com.tdem.logistica.modules.requerimientos.SlaAtencion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * Plazo de entrega de la OC (DEC-L089): dias y si son habiles (sin fines de
 * semana ni feriados de Peru, como SlaAtencion) o calendario. Con el plazo, la
 * fecha comprometida se cuenta desde la aprobacion de la OC (cuando se envia al
 * proveedor); sin aprobar, desde hoy, como referencia.
 */
public final class PlazoEntrega {

    public static final String HABILES = "HABILES";
    public static final String CALENDARIO = "CALENDARIO";
    private static final Set<String> TIPOS = Set.of(HABILES, CALENDARIO);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private PlazoEntrega() {
    }

    /** Valida y fija dias, tipo y el texto del F-LOG-09 ("5 días hábiles"). */
    public static void aplicar(OrdenAbastecimiento orden, Integer dias, String tipo) {
        if (dias == null || tipo == null || tipo.isBlank()) {
            throw ApiException.badRequest("Indica el plazo de entrega: días y si son hábiles o calendario.");
        }
        String t = tipo.trim().toUpperCase();
        if (!TIPOS.contains(t)) {
            throw ApiException.badRequest("El plazo debe ser en días hábiles o calendario.");
        }
        if (dias < 1 || dias > 365) {
            throw ApiException.badRequest("El plazo de entrega debe estar entre 1 y 365 días.");
        }
        orden.setDeliveryDays(dias);
        orden.setDeliveryDayType(t);
        orden.setDeliveryTerm(texto(dias, t));
    }

    public static String texto(int dias, String tipo) {
        return dias + (dias == 1 ? " día " : " días ") + (HABILES.equals(tipo) ? (dias == 1 ? "hábil" : "hábiles")
                : "calendario");
    }

    /** Fecha en que el proveedor debe entregar; null si la OC no tiene plazo estructurado. */
    public static LocalDate fechaComprometida(OrdenAbastecimiento o) {
        return fechaComprometida(o.getApprovedAt(), o.getDeliveryDays(), o.getDeliveryDayType());
    }

    /** Igual que la de la OC, desde sus columnas (aprobacion, dias y tipo); lo usa el desempeno del proveedor. */
    public static LocalDate fechaComprometida(Instant aprobadaEn, Integer dias, String tipo) {
        if (dias == null || tipo == null) {
            return null;
        }
        LocalDate desde = aprobadaEn != null ? aprobadaEn.atZone(LIMA).toLocalDate() : LocalDate.now(LIMA);
        return HABILES.equals(tipo) ? SlaAtencion.sumarHabiles(desde, dias) : desde.plusDays(dias);
    }

    /** Dia (en Lima) de un instante, para compararlo con la fecha comprometida. */
    public static LocalDate dia(Instant instante) {
        return instante.atZone(LIMA).toLocalDate();
    }
}
