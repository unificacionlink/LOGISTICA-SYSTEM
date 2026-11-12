package com.tdem.logistica.modules.compras;

import com.tdem.logistica.modules.compras.dto.OcrCotizacionDto;
import com.tdem.logistica.modules.compras.dto.OcrItemSugeridoDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresion de bugs reales encontrados con una cotizacion real (PDF con
 * texto embebido, membrete societario, fecha "dd-MMM-yyyy" y un
 * "Sub-Total" antes del total final): OcrExtractor.extraerDePdf() antes
 * renderizaba la pagina a imagen (fallaba por una imagen CMYK incrustada)
 * en vez de usar el texto embebido; el parser tomaba el Sub-Total en vez
 * del total real; y no habia forma de sugerir el costo por item entre
 * proveedores con formatos de tabla distintos.
 */
class CotizacionOcrParserTest {

    private static final String TEXTO_REAL =
            "SACERCORP SAC.\n"
                    + "Distribuidor oficial en Peru de la marca registrada SJCAM.\n"
                    + "RUC FECHA CIUDAD\n"
                    + "20522095797 19-Ago-2026 LIMA\n"
                    + "ITEM CODIGO (Q) P. UNI IMPORTE\n"
                    + "1 SPNGA10\n"
                    + "20 S/. 559 S/. 11,180.00\n"
                    + "2 MEMORI12\n"
                    + "8\n"
                    + "20 S/. 71 S/. 1,420\n"
                    + "Sub -Total S/. 12,600.00\n"
                    + "IGV 18% S/. 2,268.00\n"
                    + "VALOR TOTAL S/. 14,868.00\n";

    @Test
    void tomaElTotalFinalNoElSubtotalYReconoceFechaConMesEnTexto() {
        OcrCotizacionDto datos = CotizacionOcrParser.parsear(TEXTO_REAL);

        assertEquals("SACERCORP SAC.", datos.proveedorDetectado());
        assertEquals("20522095797", datos.rucDetectado());
        assertEquals(LocalDate.of(2026, 8, 19), datos.fechaDetectada());
        assertEquals("PEN", datos.monedaDetectada());
        assertEquals(0, new BigDecimal("14868.00").compareTo(datos.totalDetectado()),
                "Debe tomar VALOR TOTAL (14,868.00), no Sub-Total (12,600.00)");
    }

    @Test
    void sugiereElCostoUnitarioBuscandoElCodigoYaEnviadoEnLaRfq() {
        UUID itemSponja = UUID.randomUUID();
        UUID itemMemoria = UUID.randomUUID();
        Map<UUID, String> codigos = Map.of(itemSponja, "SPNGA10", itemMemoria, "MEMORI12");

        OcrCotizacionDto datos = CotizacionOcrParser.parsear(TEXTO_REAL, codigos);

        List<OcrItemSugeridoDto> sugerencias = datos.itemsSugeridos();
        assertEquals(2, sugerencias.size());
        OcrItemSugeridoDto sponja = sugerencias.stream().filter(s -> s.rfqItemId().equals(itemSponja)).findFirst().get();
        OcrItemSugeridoDto memoria = sugerencias.stream().filter(s -> s.rfqItemId().equals(itemMemoria)).findFirst().get();
        // El costo unitario (menor monto en la ventana) no el importe de linea (mayor).
        assertEquals(0, new BigDecimal("559").compareTo(sponja.costoSugerido()));
        assertEquals(0, new BigDecimal("71").compareTo(memoria.costoSugerido()));
    }

    @Test
    void noSugiereNadaSiElCodigoNoApareceEnElTexto() {
        OcrCotizacionDto datos = CotizacionOcrParser.parsear(TEXTO_REAL, Map.of(UUID.randomUUID(), "NOEXISTE99"));
        assertTrue(datos.itemsSugeridos().isEmpty());
    }

    /** Proveedor que no usa nuestros codigos: se reconoce cada producto por su nombre (DEC-L047). */
    static final String SIN_CODIGOS =
            "FERRETERIA LOS ANDES S.A.C.\n"
                    + "RUC 20100000021\n"
                    + "ITEM DESCRIPCION CANT P.UNIT TOTAL\n"
                    + "1 Camara endoscopio HD 3m luz LED wifi 10 85.50 855.00\n"
                    + "2 Repulsor ultrasonico canes 4 32.00 128.00\n"
                    + "3 Linterna frontal recargable 2 15.00 30.00\n"
                    + "TOTAL S/ 1,013.00\n";

    @Test
    void reconoceElProductoPorSuNombreYSeparaUnitarioDelTotal() {
        UUID camara = UUID.randomUUID();
        UUID repulsor = UUID.randomUUID();
        UUID guantes = UUID.randomUUID();
        Map<UUID, CotizacionOcrParser.ItemBuscado> items = Map.of(
                camara, new CotizacionOcrParser.ItemBuscado("AC01003-00",
                        "CAMARA ENDOSCOPIO HD 3 METROS CON LUZ LED WIFI P/CELULAR", new BigDecimal("10")),
                repulsor, new CotizacionOcrParser.ItemBuscado("AC01008-00", "REPULSOR PARA CANES/PERROS", new BigDecimal("4")),
                guantes, new CotizacionOcrParser.ItemBuscado("EP01001-00", "GUANTES DE NITRILO", new BigDecimal("20")));

        OcrCotizacionDto datos = CotizacionOcrParser.parsearItems(SIN_CODIGOS, items);

        Map<UUID, BigDecimal> costos = new java.util.HashMap<>();
        datos.itemsSugeridos().forEach(i -> costos.put(i.rfqItemId(), i.costoSugerido()));
        assertEquals(0, new BigDecimal("85.50").compareTo(costos.get(camara)), "unitario, no el total 855.00");
        assertEquals(0, new BigDecimal("32.00").compareTo(costos.get(repulsor)));
        assertTrue(!costos.containsKey(guantes), "lo que el proveedor no cotiza no se inventa");
        assertEquals("20100000021", datos.rucDetectado());
    }

    // ---------------------------------------------------------------- DEC-L053

    /** Proveedor que llama distinto a nuestros productos: nada en comun con el nombre. */
    static final String OTROS_NOMBRES =
            "SEGURIDAD INDUSTRIAL NORTE S.A.C.\n"
                    + "RUC 20100000041\n"
                    + "ITEM CODIGO DESCRIPCION CANT P.UNIT TOTAL\n"
                    + "1 VG-2231 Prot. craneal dielec. azul V-Gard 5 48.00 240.00\n"
                    + "2 Lente oscuro sombra 11\n"
                    + "3 12.50 37.50\n"
                    + "SUB TOTAL 277.50\n"
                    + "IGV 18% 49.95\n";

    @Test
    void reconoceLoAprendidoDelProveedorPorCodigoYPorDescripcion() {
        UUID casco = UUID.randomUUID();
        UUID lente = UUID.randomUUID();
        Map<UUID, CotizacionOcrParser.ItemBuscado> items = new java.util.LinkedHashMap<>();
        items.put(casco, new CotizacionOcrParser.ItemBuscado("EP02003-00", "CASCO AZUL C/SUPRESOR DIELECTRICO",
                new BigDecimal("5"), List.of(new CotizacionOcrParser.Equivalencia("VG-2231", "OTRA COSA"))));
        items.put(lente, new CotizacionOcrParser.ItemBuscado("EP01009-00", "LUNA PARA CARETA", new BigDecimal("3"),
                List.of(new CotizacionOcrParser.Equivalencia(null, CotizacionOcrParser.clave("Lente oscuro sombra 11")))));

        OcrCotizacionDto datos = CotizacionOcrParser.parsearItems(OTROS_NOMBRES, items);

        OcrItemSugeridoDto c = sugerencia(datos, casco);
        OcrItemSugeridoDto l = sugerencia(datos, lente);
        assertEquals(CotizacionOcrParser.ORIGEN_EQUIVALENCIA, c.origen(), "por el codigo del proveedor");
        assertEquals(0, new BigDecimal("48.00").compareTo(c.costoSugerido()));
        assertEquals(CotizacionOcrParser.ORIGEN_EQUIVALENCIA, l.origen(), "por su descripcion, con la tabla partida");
        assertEquals(0, new BigDecimal("12.50").compareTo(l.costoSugerido()));
        assertEquals(3, c.indiceLinea());
        assertEquals(4, l.indiceLinea());
    }

    @Test
    void sinEquivalenciasNiPalabrasEnComunNoAdivina() {
        UUID casco = UUID.randomUUID();
        OcrCotizacionDto datos = CotizacionOcrParser.parsearItems(OTROS_NOMBRES, Map.of(casco,
                new CotizacionOcrParser.ItemBuscado("EP02001-00", "CASCO DE SEGURIDAD BLANCO", new BigDecimal("5"))));
        assertTrue(datos.itemsSugeridos().isEmpty(), "la revision asistida lo resuelve eligiendo la linea");
        assertEquals(2, datos.lineas().size(), "las dos lineas con precio, sin subtotal ni IGV");
        assertEquals("VG-2231", datos.lineas().get(0).codigo());
        assertEquals("VG-2231 Prot. craneal dielec. azul V-Gard 5", datos.lineas().get(0).descripcion());
        assertEquals("Lente oscuro sombra", datos.lineas().get(1).descripcion().substring(0, 19));
    }

    @Test
    void elSkuTienePrioridadSobreLaEquivalencia() {
        UUID item = UUID.randomUUID();
        String texto = "1 SPNGA10 Esponja\n2 Otra cosa VG-2231 9.00 9.00\n20 S/. 559 S/. 11,180.00\n";
        OcrCotizacionDto datos = CotizacionOcrParser.parsearItems(texto, Map.of(item, new CotizacionOcrParser.ItemBuscado(
                "SPNGA10", "ESPONJA", new BigDecimal("20"), List.of(new CotizacionOcrParser.Equivalencia("VG-2231", null)))));
        assertEquals(CotizacionOcrParser.ORIGEN_SKU, sugerencia(datos, item).origen());
    }

    @Test
    void unitarioDeLineaYClave() {
        List<BigDecimal> importes = List.of(new BigDecimal("48.00"), new BigDecimal("240.00"));
        assertEquals(0, new BigDecimal("48.00").compareTo(CotizacionOcrParser.unitarioDeLinea(importes, new BigDecimal("5"))));
        assertEquals("PROT CRANEAL DIELEC AZUL V GARD", CotizacionOcrParser.clave("1 Prot. cráneal dielec. azul V-Gard 48.00"));
    }

    private static OcrItemSugeridoDto sugerencia(OcrCotizacionDto d, UUID id) {
        return d.itemsSugeridos().stream().filter(s -> s.rfqItemId().equals(id)).findFirst().orElseThrow();
    }
}
