package com.tdem.logistica.modules.compras;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.OrdenAbastecimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * IGV de la orden de compra (DEC-L079). Se elige en cada OC:
 * <ul>
 *   <li>MAS_IGV: los precios son valor de venta; se suma el 18%.</li>
 *   <li>IGV_INCLUIDO: los precios ya traen IGV; se desglosa hacia atras.</li>
 *   <li>NO_AFECTO: sin IGV (servicios no gravados, proveedores exonerados).</li>
 * </ul>
 * subtotal = VALOR TOTAL, tax_amount = IGV, total = PRECIO TOTAL del formato
 * F-LOG-09. Las lineas guardan siempre lo cotizado (cantidad x precio).
 */
public final class ImpuestoOrden {

    public static final String MAS_IGV = "MAS_IGV";
    public static final String IGV_INCLUIDO = "IGV_INCLUIDO";
    public static final String NO_AFECTO = "NO_AFECTO";
    public static final Set<String> MODOS = Set.of(MAS_IGV, IGV_INCLUIDO, NO_AFECTO);

    /** IGV de Peru. */
    public static final BigDecimal TASA = new BigDecimal("0.18");

    private ImpuestoOrden() {
    }

    public static String validarModo(String modo) {
        String m = modo == null ? "" : modo.trim().toUpperCase();
        if (!MODOS.contains(m)) {
            throw ApiException.badRequest("IGV invalido: usa MAS_IGV, IGV_INCLUIDO o NO_AFECTO.");
        }
        return m;
    }

    /** Fija subtotal, IGV y total de la orden a partir de la suma de sus lineas. */
    public static void aplicar(OrdenAbastecimiento orden, BigDecimal sumaLineas) {
        BigDecimal suma = sumaLineas.setScale(2, RoundingMode.HALF_UP);
        BigDecimal valor;
        BigDecimal igv;
        BigDecimal total;
        switch (orden.getTaxMode()) {
            case MAS_IGV -> {
                valor = suma;
                igv = suma.multiply(TASA).setScale(2, RoundingMode.HALF_UP);
                total = valor.add(igv);
            }
            case IGV_INCLUIDO -> {
                total = suma;
                valor = suma.divide(BigDecimal.ONE.add(TASA), 2, RoundingMode.HALF_UP);
                igv = total.subtract(valor);
            }
            default -> {
                valor = suma;
                igv = BigDecimal.ZERO.setScale(2);
                total = suma;
            }
        }
        orden.setSubtotal(valor);
        orden.setTaxAmount(igv);
        orden.setTotal(total);
    }
}
