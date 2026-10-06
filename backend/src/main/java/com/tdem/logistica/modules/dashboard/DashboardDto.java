package com.tdem.logistica.modules.dashboard;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Indicadores del Inicio (DEC-L020). Lo arma una sola consulta SQL que devuelve
 * JSON con estas mismas claves, asi el dashboard cuesta una ida y vuelta a la BD.
 * Los promedios vienen null cuando no hay datos en el periodo.
 */
public record DashboardDto(
        long abiertos,
        long creados,
        long completados,
        BigDecimal horasAprobacion,
        BigDecimal diasCiclo,
        long rfqEnviadas,
        long rfqRespondidas,
        BigDecimal diasRespuesta,
        List<Ahorro> ahorro,
        long comprasConComparativo,
        long comprasTotal,
        List<Etapa> etapas,
        List<Semana> semanas,
        List<Proveedor> proveedores,
        List<Comparativo> comparativos) {

    /** Monto mayor cotizado - monto elegido, por moneda (no se mezclan PEN y USD). */
    public record Ahorro(String moneda, BigDecimal monto, long compras) {
    }

    /** Trabajo pendiente hoy; un REQ puede estar en mas de una etapa (items en paralelo). */
    public record Etapa(String codigo, long cantidad) {
    }

    public record Semana(String semana, long creados) {
    }

    public record Proveedor(String proveedor, long enviadas, long respondidas, BigDecimal diasRespuesta,
                            long elegidas) {
    }

    public record Comparativo(String codigo, String proveedor, long cotizaciones, String moneda,
                              BigDecimal elegido, BigDecimal mayor, BigDecimal ahorro, OffsetDateTime fecha) {
    }
}
