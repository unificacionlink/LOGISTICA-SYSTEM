package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.AppConstants.EstadoOrden;
import com.tdem.logistica.entity.OrdenAbastecimiento;

import java.util.List;

/**
 * Reglas del ciclo de la OC (Lote 17, DEC-L061): aprobar, liberar y recibir.
 * El pago no aparece aca a proposito: es control y nunca frena la recepcion.
 */
public final class FlujoOrden {

    /** Estados en los que el almacen recibe (si ademas la OC esta liberada). */
    public static final List<String> ESTADOS_RECIBIBLES =
            List.of(EstadoOrden.EMITIDA, EstadoOrden.EN_ATENCION, EstadoOrden.RECEPCION_PARCIAL);

    private FlujoOrden() {
    }

    /** Por que el almacen no puede recibir esta OC todavia; null si puede o si ya no aplica. */
    public static String bloqueoRecepcion(OrdenAbastecimiento o) {
        if (o.isServicesOnly()) {
            return null;  // orden de servicios: no pasa por el almacen (DEC-L080)
        }
        if (EstadoOrden.POR_APROBAR.equals(o.getStatus())) {
            return "Falta aprobar la orden de compra.";
        }
        if (ESTADOS_RECIBIBLES.contains(o.getStatus()) && o.getReleasedAt() == null) {
            return "Falta liberar la orden de compra.";
        }
        return null;
    }

    public static boolean recibible(OrdenAbastecimiento o) {
        return !o.isServicesOnly() && ESTADOS_RECIBIBLES.contains(o.getStatus()) && o.getReleasedAt() != null;
    }
}
