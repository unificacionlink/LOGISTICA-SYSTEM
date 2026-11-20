package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Pantalla principal de Proveedores: indicadores y una fila por proveedor (DEC-L069/L070). */
public record PanelProveedoresDto(LocalDate hoy, Kpis kpis, List<Fila> proveedores) {

    /** Las evaluaciones por vencer, vencidas y sin evaluacion cuentan solo a los habilitados activos. */
    public record Kpis(int registrados, int habilitados, int enRevision, int porVencer, int vencidas,
                       int sinEvaluacion) {
    }

    public record Fila(
            UUID supplierId,
            String ruc,
            String legalName,
            String tradeName,
            String contacto,
            String supplierType,
            String status,
            boolean active,
            String city,
            int productos,
            String categorias,
            /** SKU y nombres de sus productos, para buscar por producto. */
            String busqueda,
            BigDecimal ultimaNota,
            BigDecimal escala,
            String ultimoResultado,
            String ultimoTipo,
            LocalDate ultimaEvaluacion,
            LocalDate proximaEvaluacion,
            /** VIGENTE, PROXIMO_A_VENCER, VENCIDO o SIN_EVALUACION. */
            String vigencia,
            /** Empresa en la que se registro (DEC-L081). */
            String empresaCode) {
    }
}
