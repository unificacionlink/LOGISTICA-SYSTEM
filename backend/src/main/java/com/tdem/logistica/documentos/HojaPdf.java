package com.tdem.logistica.documentos;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base de los formatos del sistema de gestion dibujados a mano con PDFBox (DEC-L102):
 * pagina A4, cabecera logo | FORMATO + titulo | codigo/version/fecha de aprobacion,
 * y las primitivas de dibujo (celdas, texto que se parte, imagenes). Cada formato
 * pone su contenido; {@link PdfOrdenCompra} es anterior y conserva las suyas.
 */
abstract class HojaPdf {

    private static final Logger log = LoggerFactory.getLogger(HojaPdf.class);

    protected static final PDRectangle PAGINA = PDRectangle.A4;
    protected static final float X0 = 36f;
    protected static final float W = PAGINA.getWidth() - 2 * X0;
    protected static final float Y_MIN = 50f;
    protected static final float TOPE = PAGINA.getHeight() - 36f;

    protected static final Color NEGRO = Color.BLACK;
    protected static final Color TENUE = new Color(110, 110, 110);
    protected static final Color ROJO = new Color(220, 30, 30);

    protected final PDFont courier = new PDType1Font(Standard14Fonts.FontName.COURIER);
    protected final PDFont courierBold = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
    protected final PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    protected final PDFont helveticaBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<String, Boolean> codificable = new HashMap<>();

    protected final PDDocument doc = new PDDocument();
    protected PDPageContentStream cs;
    protected float y;

    /** Lo que repite cada pagina (la cabecera del formato). */
    protected abstract void cabecera();

    // ------------------------------------------------------------------ cabecera del formato

    /**
     * Recuadro superior de los formatos: logo (o el nombre de la empresa), "FORMATO" y
     * el titulo, y la tabla de codigo, version y fecha de aprobacion.
     */
    protected void cabeceraFormato(PDImageXObject logo, String empresa, String titulo, String codigo, String version,
                                   String fechaAprobacion) {
        float alto = 58f;
        float base = TOPE - alto;
        float wLogo = 150f;
        float wDerecha = 165f;
        float wMedio = W - wLogo - wDerecha;
        float xMedio = X0 + wLogo;
        float xDerecha = xMedio + wMedio;
        rect(X0, base, W, alto);
        linea(xMedio, base, xMedio, TOPE);
        linea(xDerecha, base, xDerecha, TOPE);
        if (logo != null) {
            float escala = Math.min((wLogo - 12f) / logo.getWidth(), (alto - 12f) / logo.getHeight());
            float lw = logo.getWidth() * escala;
            float lh = logo.getHeight() * escala;
            io(() -> cs.drawImage(logo, X0 + (wLogo - lw) / 2, base + (alto - lh) / 2, lw, lh));
        } else {
            centrado(empresa, helveticaBold, 14f, X0, wLogo, base + alto / 2 - 5f, NEGRO);
        }
        float filaFormato = 14f;
        linea(xMedio, TOPE - filaFormato, xDerecha, TOPE - filaFormato);
        centrado("FORMATO", courierBold, 7.5f, xMedio, wMedio, TOPE - filaFormato + 4f, NEGRO);
        List<String> lineasTitulo = envolver(titulo, courierBold, 8f, wMedio - 12f);
        float yTitulo = base + (alto - filaFormato) / 2 + (lineasTitulo.size() - 1) * 4.5f - 3f;
        for (String l : lineasTitulo) {
            centrado(l, courierBold, 8f, xMedio, wMedio, yTitulo, NEGRO);
            yTitulo -= 9f;
        }
        float wEtiqueta = 85f;
        linea(xDerecha + wEtiqueta, base, xDerecha + wEtiqueta, TOPE);
        float[] filas = {14f, 14f, 30f};
        String[][] textos = {{"CÓDIGO:", codigo}, {"VERSIÓN:", version}, {"FECHA\nAPROBACIÓN:", fechaAprobacion}};
        float tope = TOPE;
        for (int i = 0; i < filas.length; i++) {
            float abajo = tope - filas[i];
            if (i < filas.length - 1) {
                linea(xDerecha, abajo, X0 + W, abajo);
            }
            String[] partes = textos[i][0].split("\n");
            float yTexto = abajo + filas[i] / 2 + (partes.length - 1) * 4.5f - 2.5f;
            for (String p : partes) {
                centrado(p, courierBold, 7.5f, xDerecha, wEtiqueta, yTexto, NEGRO);
                yTexto -= 9f;
            }
            centrado(textos[i][1], courier, 7.5f, xDerecha + wEtiqueta, wDerecha - wEtiqueta,
                    abajo + filas[i] / 2 - 2.5f, NEGRO);
            tope = abajo;
        }
        y = base - 18f;
    }

    // ------------------------------------------------------------------ paginas

    protected void nuevaPagina() {
        cerrarPagina();
        PDPage pagina = new PDPage(PAGINA);
        doc.addPage(pagina);
        try {
            cs = new PDPageContentStream(doc, pagina);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cabecera();
    }

    /** Salta de pagina si lo que sigue ({@code alto}) no cabe. */
    protected void asegurar(float alto) {
        if (y - alto < Y_MIN) {
            nuevaPagina();
        }
    }

    private void cerrarPagina() {
        if (cs != null) {
            io(() -> cs.close());
            cs = null;
        }
    }

    /** Cierra el documento; con mas de una pagina, numera "Pagina n de m". */
    protected byte[] terminar() {
        cerrarPagina();
        int total = doc.getNumberOfPages();
        if (total > 1) {
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream pie = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs = pie;
                    derecha("Página " + (i + 1) + " de " + total, helvetica, 7f, X0, W, 24f, TENUE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            cs = null;
        }
        try (doc; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ primitivas

    protected void rect(float x, float yAbajo, float w, float h) {
        io(() -> {
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.6f);
            cs.addRect(x, yAbajo, w, h);
            cs.stroke();
        });
    }

    protected void linea(float x1, float y1, float x2, float y2) {
        io(() -> {
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.6f);
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        });
    }

    protected void escribir(String texto, PDFont f, float tam, float x, float yBase, Color color) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        String seguro = seguro(texto, f);
        io(() -> {
            cs.beginText();
            cs.setFont(f, tam);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, yBase);
            cs.showText(seguro);
            cs.endText();
        });
    }

    protected void centrado(String texto, PDFont f, float tam, float x, float ancho, float yBase, Color color) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        escribir(texto, f, tam, x + (ancho - ancho(seguro(texto, f), f, tam)) / 2, yBase, color);
    }

    protected void derecha(String texto, PDFont f, float tam, float x, float ancho, float yBase, Color color) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        escribir(texto, f, tam, x + ancho - ancho(seguro(texto, f), f, tam) - 3f, yBase, color);
    }

    /** Parte el texto en lineas que caben en {@code ancho}; respeta los saltos de linea. */
    protected List<String> envolver(String texto, PDFont f, float tam, float ancho) {
        List<String> lineas = new ArrayList<>();
        if (texto == null) {
            return lineas;
        }
        for (String parrafo : texto.split("\\R", -1)) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.trim().split("\\s+")) {
                if (palabra.isEmpty()) {
                    continue;
                }
                String candidato = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (ancho(seguro(candidato, f), f, tam) <= ancho) {
                    actual.setLength(0);
                    actual.append(candidato);
                    continue;
                }
                if (!actual.isEmpty()) {
                    lineas.add(actual.toString());
                    actual.setLength(0);
                }
                // Palabra mas ancha que la columna (enlaces, codigos): se corta por caracteres.
                String resto = palabra;
                while (ancho(seguro(resto, f), f, tam) > ancho && resto.length() > 1) {
                    int corte = resto.length() - 1;
                    while (corte > 1 && ancho(seguro(resto.substring(0, corte), f), f, tam) > ancho) {
                        corte--;
                    }
                    lineas.add(resto.substring(0, corte));
                    resto = resto.substring(corte);
                }
                actual.append(resto);
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }

    protected float ancho(String texto, PDFont f, float tam) {
        try {
            return f.getStringWidth(texto) / 1000f * tam;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Helvetica/Courier estandar solo cubren WinAnsi: lo demas se reemplaza. */
    protected String seguro(String texto, PDFont f) {
        StringBuilder sb = new StringBuilder(texto.length());
        texto.codePoints().forEach(cp -> {
            if (cp == '\t') {
                sb.append(' ');
                return;
            }
            String c = new String(Character.toChars(cp));
            boolean ok = codificable.computeIfAbsent(f.getName() + c, k -> {
                try {
                    f.encode(c);
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
            sb.append(ok ? c : (cp == '…' ? "..." : "?"));
        });
        return sb.toString();
    }

    protected PDImageXObject imagen(byte[] bytes, String nombre) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return PDImageXObject.createFromByteArray(doc, bytes, nombre);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Imagen '{}' del formato no se pudo leer: {}", nombre, e.getMessage());
            return null;
        }
    }

    protected static String guion(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    protected interface AccionIo {
        void ejecutar() throws IOException;
    }

    protected static void io(AccionIo accion) {
        try {
            accion.ejecutar();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
