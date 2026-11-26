package com.tdem.logistica.storage;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
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
    public String extraerTexto(byte[] contenido, String contentType) {
        return extraerTexto(contenido, contentType, false);
    }

    /**
     * @param reforzado ademas del umbral global, una segunda pasada con umbral
     *                  adaptativo (DEC-L097): rescata el texto tenue de los tickets
     *                  termicos, pero ensucia las fotos, asi que se suman las
     *                  lecturas y el que interpreta se queda con lo que reconoce.
     *                  Una tercera, adaptativa sobre la imagen con mas contraste
     *                  (DEC-L098). Tarda el triple; para comprobantes de caja chica.
     */
    public synchronized String extraerTexto(byte[] contenido, String contentType, boolean reforzado) {
        try {
            if ("application/pdf".equals(contentType)) {
                return extraerDePdf(contenido, reforzado);
            }
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(contenido));
            if (imagen == null) {
                throw new UncheckedIOException(new IOException("Formato de imagen no reconocido."));
            }
            return ocr(imagen, imagen, reforzado);
        } catch (TesseractException | IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento con OCR.", new IOException(e));
        }
    }

    /** @param paraContraste la misma imagen, de donde sale la lectura con contraste. */
    private String ocr(BufferedImage imagen, BufferedImage paraContraste, boolean reforzado) throws TesseractException {
        if (!reforzado) {
            return tesseract.doOCR(imagen);
        }
        try {
            String global = tesseract.doOCR(imagen);
            tesseract.setVariable("thresholding_method", "1");
            String adaptativo = tesseract.doOCR(imagen);
            String contrastado = tesseract.doOCR(realzarContraste(paraContraste));
            return global + "\n\f\n" + adaptativo + "\n\f\n" + contrastado;  // \f separa las lecturas
        } finally {
            tesseract.setVariable("thresholding_method", "0");
        }
    }

    /**
     * Sube el contraste recortando (DEC-L098): el 2 % mas oscuro pasa a negro y todo
     * lo que es tan claro como la mediana (el papel) a blanco. Estirar sin recortar
     * no cambia nada al binarizar; recortar si oscurece el trazo tenue de los
     * tickets termicos y de las fotos, y el umbral adaptativo lo rescata.
     */
    static BufferedImage realzarContraste(BufferedImage imagen) {
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        // Luminancia directa de los canales: la conversion de color de Java2D (con
        // gamma) aclara el trazo tenue y se pierde lo que se queria rescatar.
        int[] rgb = imagen.getRGB(0, 0, w, h, null, 0, w);
        int[] pixeles = new int[rgb.length];
        for (int k = 0; k < rgb.length; k++) {
            int c = rgb[k];
            pixeles[k] = ((c >> 16 & 255) * 3 + (c >> 8 & 255) * 6 + (c & 255)) / 10;
        }
        BufferedImage gris = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        var raster = gris.getRaster();
        int[] histograma = new int[256];
        for (int p : pixeles) {
            histograma[p]++;
        }
        int negro = percentil(histograma, pixeles.length, 0.02);
        int blanco = Math.max(negro + 1, percentil(histograma, pixeles.length, 0.50));
        for (int k = 0; k < pixeles.length; k++) {
            pixeles[k] = Math.clamp((pixeles[k] - negro) * 255L / (blanco - negro), 0, 255);
        }
        raster.setPixels(0, 0, w, h, pixeles);
        return gris;
    }

    private static int percentil(int[] histograma, int total, double fraccion) {
        long objetivo = (long) (fraccion * total);
        long acumulado = 0;
        for (int v = 0; v < histograma.length; v++) {
            acumulado += histograma[v];
            if (acumulado > objetivo) {
                return v;
            }
        }
        return 255;
    }

    private String extraerDePdf(byte[] contenido, boolean reforzado) throws IOException {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            // La mayoria de las cotizaciones llegan como PDF con texto real (generado
            // desde Word/Excel/un sistema de facturacion), no como un escaneo. Si ya
            // hay texto embebido, usarlo es mas rapido y preciso que renderizar a
            // imagen y correr OCR -- y evita el bug real que motivo este cambio:
            // renderImageWithDPI() falla si la pagina trae una imagen incrustada que
            // ImageIO no puede decodificar (tipico con JPEG en espacio de color CMYK,
            // frecuente en logos/fotos de producto pegados en la cotizacion).
            //
            // Pagina por pagina (DEC-L097): un PDF puede juntar una foto escaneada (el
            // comprobante) con paginas de texto real (su OC). La pagina con texto se lee
            // tal cual y la que no lo tiene pasa por OCR; una que falle (misma causa de
            // arriba) no tira abajo la extraccion de las demas.
            StringBuilder texto = new StringBuilder();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(documento);
            int paginas = Math.min(documento.getNumberOfPages(), MAX_PAGINAS_PDF);
            for (int i = 0; i < paginas; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String embebido = stripper.getText(documento).trim();
                if (embebido.length() >= MIN_CARACTERES_TEXTO_EMBEBIDO) {
                    texto.append(embebido).append("\n\f\n");
                    continue;
                }
                try {
                    BufferedImage imagen = renderer.renderImageWithDPI(i, 300);
                    // La pagina dibujada en gris conserva mejor el trazo tenue para
                    // la lectura con contraste; las otras dos, sobre la de color.
                    BufferedImage gris = reforzado ? renderer.renderImageWithDPI(i, 300, ImageType.GRAY) : null;
                    texto.append(ocr(imagen, gris, reforzado)).append("\n\f\n");
                } catch (Exception e) {
                    log.warn("No se pudo renderizar/leer la pagina {} del PDF, se omite", i, e);
                }
            }
            return texto.toString();
        }
    }
}
