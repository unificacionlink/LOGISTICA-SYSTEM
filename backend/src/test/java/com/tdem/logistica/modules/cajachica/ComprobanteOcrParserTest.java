package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.modules.cajachica.dto.LecturaComprobanteDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DEC-L095: lo que se lee del comprobante de caja chica; lo dudoso queda vacio. */
class ComprobanteOcrParserTest {

    private static final List<String> RUC_PROPIOS = List.of("20522095797", "20601624088", "20610807071");
    private static final List<String> NOMBRES_PROPIOS = List.of("TECNOLOGIA DESARROLLO Y MEDICION S.R.L.",
            "GONZALES Y DE LA CRUZ SAC - GOYDEL", "METROLOGIA E INNOVACIÓN S.A.C.");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 26);

    private static LecturaComprobanteDto leer(String texto) {
        return ComprobanteOcrParser.leer(texto, RUC_PROPIOS, NOMBRES_PROPIOS, HOY);
    }

    @Test
    void facturaElectronicaCompleta() {
        LecturaComprobanteDto l = leer("""
                FERRETERÍA EL MAESTRO S.A.C.
                Av. Los Álamos 123 - Lima
                RUC: 20512345678
                FACTURA ELECTRÓNICA
                F001-00012345
                Fecha de Emisión: 20/11/2026
                Señor(es): TECNOLOGIA DESARROLLO Y MEDICION S.R.L.
                RUC: 20522095797
                Cant. Unidad Descripción P.Unit Importe
                2 UND Cinta de embalaje 3.50 7.00
                1 NIU Guantes de nitrilo 12.00 12.00
                OP. GRAVADA 16.10
                IGV 18% 2.90
                IMPORTE TOTAL S/ 19.00
                """);
        assertEquals("FACTURA", l.tipo());
        assertEquals("F001", l.serie());
        assertEquals("00012345", l.numero());
        assertEquals("20512345678", l.ruc(), "el RUC del emisor, no el de TDEM");
        assertEquals("FERRETERÍA EL MAESTRO S.A.C.", l.proveedor());
        assertEquals(LocalDate.of(2026, 11, 20), l.fecha());
        assertEquals(0, l.total().compareTo(new BigDecimal("19.00")));
        assertEquals(2, l.lineas().size());
        assertEquals("CINTA DE EMBALAJE", l.lineas().get(0).descripcion());
        assertEquals(0, l.lineas().get(0).cantidad().compareTo(BigDecimal.valueOf(2)));
        assertEquals(0, l.lineas().get(0).precioUnitario().compareTo(new BigDecimal("3.50")));
    }

    @Test
    void preciosSinIgvSeDevuelvenConIgvSiAsiCuadraElTotal() {
        LecturaComprobanteDto l = leer("""
                COMERCIAL SANTA ROSA E.I.R.L. RUC 10456789012
                FACTURA ELECTRONICA F002-881
                FECHA EMISION 2026-11-25
                Descripcion Cant UM P.Unit Valor
                Pilas AA alcalinas 4 UND 10.00 40.00
                SUB TOTAL 40.00
                IGV 7.20
                TOTAL 47.20
                """);
        assertEquals("COMERCIAL SANTA ROSA E.I.R.L.", l.proveedor());
        assertEquals("10456789012", l.ruc());
        assertEquals(LocalDate.of(2026, 11, 25), l.fecha());
        assertEquals(0, l.total().compareTo(new BigDecimal("47.20")));
        assertEquals(1, l.lineas().size());
        assertEquals("PILAS AA ALCALINAS", l.lineas().get(0).descripcion());
        assertEquals(0, l.lineas().get(0).precioUnitario().compareTo(new BigDecimal("11.80")), "precio con IGV");
    }

    @Test
    void loIlegibleQuedaVacio() {
        LecturaComprobanteDto l = leer("~~ ;;. iI|l 0O 12 .. ,,\nxx 99/99/9999");
        assertNull(l.tipo());
        assertNull(l.serie());
        assertNull(l.ruc());
        assertNull(l.proveedor());
        assertNull(l.fecha());
        assertNull(l.total());
        assertTrue(l.lineas().isEmpty());
        assertNull(leer("  ").tipo());
    }

    @Test
    void noLlenaFechaFuturaNiLineasQueNoCuadran() {
        LecturaComprobanteDto l = leer("""
                BOLETA DE VENTA B001-77
                FECHA: 30/12/2026
                1 UND Agua mineral 2.50 9.99
                TOTAL 9.99
                """);
        assertEquals("BOLETA", l.tipo());
        assertEquals("B001", l.serie());
        assertNull(l.fecha(), "una fecha futura no se llena");
        assertTrue(l.lineas().isEmpty(), "1 x 2.50 no es 9.99: la linea no se llena");
        assertEquals(0, l.total().compareTo(new BigDecimal("9.99")));
    }
}
