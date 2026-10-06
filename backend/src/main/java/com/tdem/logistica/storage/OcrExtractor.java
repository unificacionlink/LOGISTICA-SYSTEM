package com.tdem.logistica.storage;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * OCR gratuito y local (Tesseract via Tess4j): el documento nunca sale del
 * servidor, sin costo ni API externa. Usado para pre-llenar (nunca guardar
 * ciegamente) datos de cotizaciones de proveedor subidas como foto/PDF.
 */
@Component
public class OcrExtractor {

    private static final Logger log = LoggerFactory.getLogger(OcrExtractor.class);
    private static final int MAX_PAGINAS_PDF = 3;
    private static final int MIN_CARACTERES_TEXTO_EMBEBIDO = 20;

    private final Tesseract tesseract;

    public OcrExtractor() {
        try {
            Path tessdataDir = Files.createTempDirectory("tessdata");
            try (InputStream in = getClass().getResourceAsStream("/tessdata/spa.traineddata")) {
                if (in == null) {
                    throw new IOException("No se encontro tessdata/spa.traineddata en el classpath.");
                }
                Files.copy(in, tessdataDir.resolve("spa.traineddata"), StandardCopyOption.REPLACE_EXISTING);
            }
            this.tesseract = new Tesseract();
            tesseract.setDatapath(tessdataDir.toString());
            tesseract.setLanguage("spa");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo inicializar Tesseract OCR.", e);
        }
    }

    /** Extrae el texto de una imagen o de las primeras paginas de un PDF. */
    public synchronized String extraerTexto(byte[] contenido, String contentType) {
        try {
            if ("application/pdf".equals(contentType)) {
                return extraerDePdf(contenido);
            }
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
            if (imagen == null) {
                throw new UncheckedIOException(new IOException("Formato de imagen no reconocido."));
            }
            return tesseract.doOCR(imagen);
        } catch (TesseractException | IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento con OCR.", new IOException(e));
        }
    }

    private String extraerDePdf(byte[] contenido) throws IOException, TesseractException {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            // La mayoria de las cotizaciones llegan como PDF con texto real (generado
            // desde Word/Excel/un sistema de facturacion), no como un escaneo. Si ya
            // hay texto embebido, usarlo es mas rapido y preciso que renderizar a
            // imagen y correr OCR -- y evita el bug real que motivo este cambio:
            // renderImageWithDPI() falla si la pagina trae una imagen incrustada que
            // ImageIO no puede decodificar (tipico con JPEG en espacio de color CMYK,
            // frecuente en logos/fotos de producto pegados en la cotizacion).
            String textoEmbebido = new PDFTextStripper().getText(documento).trim();
            if (textoEmbebido.length() >= MIN_CARACTERES_TEXTO_EMBEBIDO) {
                return textoEmbebido;
            }

            // Sin texto embebido: es un PDF escaneado (solo imagen), hace falta OCR.
            // Cada pagina se renderiza aparte y una que falle (misma causa de arriba)
            // no debe tirar abajo la extraccion de las demas.
            StringBuilder texto = new StringBuilder();
            PDFRenderer renderer = new PDFRenderer(documento);
            int paginas = Math.min(documento.getNumberOfPages(), MAX_PAGINAS_PDF);
            for (int i = 0; i < paginas; i++) {
                try {
                    BufferedImage imagen = renderer.renderImageWithDPI(i, 200);
                    texto.append(tesseract.doOCR(imagen)).append('\n');
                } catch (Exception e) {
                    log.warn("No se pudo renderizar/leer la pagina {} del PDF, se omite", i, e);
                }
            }
            return texto.toString();
        }
    }
}
