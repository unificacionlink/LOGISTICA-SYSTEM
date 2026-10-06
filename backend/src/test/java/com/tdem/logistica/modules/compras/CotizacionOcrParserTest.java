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
}
