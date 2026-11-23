package com.tdem.logistica.modules.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pestana Gasto del dashboard (DEC-L083): ordenes de compra aprobadas en el
 * periodo, sin IGV (VALOR TOTAL del F-LOG-09) y siempre por moneda (PEN y USD
 * no se suman). Cada lista viene ordenada de mayor a menor dentro de su moneda.
 */
public record GastoDto(
        List<Moneda> monedas,
        List<String> meses,
        List<Monto> porEmpresa,
        List<Monto> porMes,
        List<Monto> porCeco,
        List<Monto> porFamilia,
        List<Monto> proveedores) {

    public record Moneda(String moneda, BigDecimal monto, long ordenes, long proveedores) {
    }

    /**
     * clave = empresa, mes (YYYY-MM), codigo de CECO, familia o proveedor; null = sin CECO
     * (compra para stock). detalle = nombre del CECO o empresa del CECO. ordenes solo en
     * empresa y proveedores (en CECO y familia una OC se reparte).
     */
    public record Monto(String clave, String detalle, String moneda, BigDecimal monto, Long ordenes) {
    }
}
