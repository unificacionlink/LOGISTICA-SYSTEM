package com.tdem.logistica.documentos;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Maqueta del formato F-LOG-09 (DEC-L079), sin base de datos. */
class PdfOrdenCompraTest {

    @Test
    void ordenLargaSaltaDePaginaYRepiteCabecera() throws IOException {
        List<PdfOrdenCompra.Linea> lineas = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            lineas.add(new PdfOrdenCompra.Linea("AC0100" + i + "-00",
                    "Guantes de nitrilo talla M, caja x 100 unidades, color azul (línea " + i + ")",
                    "CAJA", "4", "25.00", "100.00"));
        }
        byte[] pdf = PdfOrdenCompra.generar(datos("EMITIDA", true, lineas));
        // Para revisarlo a ojo: target/oc-flog09-muestra.pdf
        Files.write(Path.of("target", "oc-flog09-muestra.pdf"), pdf);

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2, "45 lineas no caben en una pagina");
            String texto = new PDFTextStripper().getText(doc);
            // Cabecera (codigo del formato) y pie (plantilla) de cada pagina.
            assertEquals(2 * doc.getNumberOfPages(), texto.split("F-LOG-09", -1).length - 1,
                    "la cabecera del formato en cada pagina");
            assertTrue(texto.contains("Página 1 de " + doc.getNumberOfPages()));
            assertFalse(texto.contains("PENDIENTE DE APROBACIÓN"));
            assertTrue(texto.contains("serán tratados por TDEM"));
        }
    }

    @Test
    void anuladaLoDiceYNoLlevaFirmas() throws IOException {
        byte[] pdf = PdfOrdenCompra.generar(datos("ANULADA", false,
                List.of(new PdfOrdenCompra.Linea("X-1", "Servicio", "UND", "1", "10.00", "10.00"))));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(doc);
            assertTrue(texto.contains("ORDEN ANULADA"));
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    private static PdfOrdenCompra.Datos datos(String estado, boolean aprobada, List<PdfOrdenCompra.Linea> lineas)
            throws IOException {
        byte[] logo;
        try (InputStream in = PdfOrdenCompraTest.class.getResourceAsStream("/documentos/logo-tdem.png")) {
            logo = in.readAllBytes();
        }
        return new PdfOrdenCompra.Datos("OC-TDEMSRL-2026-0001", estado, aprobada, "TDEM", "20522095797",
                "JR. TARICA 5202-URB. VILLA DEL NORTE-LIMA - LIMA - LOS OLIVOS", "922257019", logo,
                "DISTRIBUIDORA DE EQUIPOS DE PROTECCION PERSONAL INDUSTRIAL S.A.C.", "20100000015", "20/11/2026",
                "7 días hábiles", "Rosa Diaz", "987654321", "Almacén central - Jr. Tarica 5202, Los Olivos",
                "Credito 30", "PEN", lineas, "4,500.00", "18%", "810.00", "5,310.00", null,
                List.of("BCP · Cta. corriente PEN · N° 191-1234567-0-11 · CCI 00219100123456701155"),
                List.of("Banco de la Nacion · Cta. detracción PEN · N° 00-000-123456"),
                "Entregar con guía de remisión y certificado de calidad.", aprobada ? logo : null,
                aprobada ? logo : null, "F-LOG-09 v1 · Generado el 20/11/2026 15:00 por Logística");
    }
}
