package com.tdem.logistica.common;

import java.util.Set;

/**
 * Constantes reutilizables — equivalente Java de src/config/constants.ts
 * del stack anterior. Fieles a los CHECK constraints reales de la BD
 * (ver db_design_reference/migrations/0015_requerimientos.sql).
 */
public final class AppConstants {

    private AppConstants() {
    }

    public static final class EstadoRequerimiento {
        public static final String BORRADOR = "BORRADOR";
        public static final String PENDIENTE_APROBACION = "PENDIENTE_APROBACION";
        /** RF-006: devuelto al solicitante con motivo; lo corrige y lo reenvia. */
        public static final String OBSERVADO = "OBSERVADO";
        public static final String VERIFICACION_STOCK = "VERIFICACION_STOCK";
        public static final String EN_ATENCION = "EN_ATENCION";
        public static final String ATENCION_PARCIAL = "ATENCION_PARCIAL";
        public static final String ATENCION_COMPLETA = "ATENCION_COMPLETA";
        public static final String RECHAZADO = "RECHAZADO";
        /** Estados en los que el dueno puede editar y enviar a aprobacion. */
        public static final Set<String> EDITABLES = Set.of(BORRADOR, OBSERVADO);
    }

    public static final class PrioridadRequerimiento {
        public static final String NORMAL = "NORMAL";
        public static final String URGENTE = "URGENTE";
        public static final String CRITICA = "CRITICA";
        public static final Set<String> VALORES = Set.of(NORMAL, URGENTE, CRITICA);
    }

    public static final class PropositoRequerimiento {
        public static final String CONSUMO = "CONSUMO";
        public static final String VENTA = "VENTA";
        public static final String PRESTAMO = "PRESTAMO";
        public static final String IMPLEMENTACION = "IMPLEMENTACION";
        public static final Set<String> VALORES = Set.of(CONSUMO, VENTA, PRESTAMO, IMPLEMENTACION);
    }

    public static final class Roles {
        public static final String SOLICITANTE = "SOLICITANTE";
        public static final String LOGISTICA = "LOGISTICA";
        public static final String APROBADOR = "APROBADOR";
        public static final String ADMIN = "ADMIN";
        /** Administrador de presupuestos (V38.7, DEC-L030). */
        public static final String PYC = "PYC";
        /** Decide en el ambito de sus CECOs (V38.7, DEC-L030). */
        public static final String GERENCIA = "GERENCIA";
    }

    public static final class EstadoAprobacion {
        public static final String PENDIENTE = "PENDIENTE";
        public static final String APROBADO = "APROBADO";
        public static final String OBSERVADO = "OBSERVADO";
        public static final String RECHAZADO = "RECHAZADO";
    }

    public static final class EntidadAprobacion {
        public static final String REQUERIMIENTO = "REQUERIMIENTO";
    }
}
