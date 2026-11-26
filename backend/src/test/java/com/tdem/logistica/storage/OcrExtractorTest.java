package com.tdem.logistica.storage;

import com.tdem.logistica.modules.compras.CotizacionOcrParser;
import com.tdem.logistica.modules.compras.dto.OcrCotizacionDto;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo del OCR (sin contexto de Spring, sin BD): genera una
 * imagen sintetica con texto conocido y verifica que Tesseract cargue sus
 * librerias nativas correctamente y que el parser de regex saque los campos
 * esperados. Corre offline: no depende de un documento real ni de red.
 */
class OcrExtractorTest {

    @Test
    void extraeTextoYCamposDeUnaCotizacionSintetica() throws IOException {
        BufferedImage imagen = generarImagenDeCotizacion();
        byte[] png = aBytesPng(imagen);

        String texto = new OcrExtractor().extraerTexto(png, "image/png");
        assertTrue(texto.contains("20123456789"), "Deberia reconocer el RUC. Texto OCR: " + texto);
        assertTrue(texto.toUpperCase().contains("TOTAL"), "Deberia reconocer la palabra TOTAL. Texto OCR: " + texto);

        OcrCotizacionDto datos = CotizacionOcrParser.parsear(texto);
        assertEquals("20123456789", datos.rucDetectado());
        assertEquals("PEN", datos.monedaDetectada());
        assertEquals(0, new BigDecimal("350.00").compareTo(datos.totalDetectado()));
        assertEquals(5, datos.plazoDetectadoDias());
    }

    /** DEC-L098: el trazo tenue de un ticket termico pasa a negro y el papel a blanco. */
    @Test
    void realzarContrasteOscureceElTrazoTenueYBlanqueaElPapel() {
        BufferedImage ticket = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = ticket.createGraphics();
        g.setColor(new Color(225, 225, 225));  // papel gris
        g.fillRect(0, 0, 200, 100);
        g.setColor(new Color(195, 195, 195));  // texto tenue
        g.fillRect(20, 40, 60, 10);
        g.dispose();

        var r = OcrExtractor.realzarContraste(ticket).getRaster();
        assertEquals(255, r.getSample(150, 80, 0), "el papel queda blanco");
        assertEquals(0, r.getSample(40, 45, 0), "el trazo tenue queda negro");
    }

    private BufferedImage generarImagenDeCotizacion() {
        BufferedImage imagen = new BufferedImage(900, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 900, 300);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString("PROVEEDOR DEMO SAC", 30, 50);
        g.drawString("RUC: 20123456789", 30, 100);
        g.drawString("Fecha: 15/03/2026", 30, 150);
        g.drawString("TOTAL: S/ 350.00", 30, 200);
        g.drawString("Plazo de entrega: 5 dias", 30, 250);
        g.dispose();
        return imagen;
    }

    private byte[] aBytesPng(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", out);
        return out.toByteArray();
    }
}
