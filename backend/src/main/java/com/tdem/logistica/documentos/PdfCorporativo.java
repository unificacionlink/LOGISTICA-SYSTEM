package com.tdem.logistica.documentos;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF-038: maqueta comun de los PDFs corporativos (PDFBox, A4 vertical).
 * Cabecera con logo (TDEM) o nombre de la empresa, titulo, codigo y empresa;
 * pie con plantilla/version, quien y cuando lo genero, y "Pagina i de n".
 * El contenido se arma con secciones, grillas de campos, parrafos y tablas
 * que saltan de pagina solas (la tabla repite su encabezado).
 *
 * Usa Helvetica (fuente estandar, sin archivos que embeber): cubre tildes y
 * enes; cualquier caracter fuera de WinAnsi se reemplaza al escribir.
 */
public final class PdfCorporativo {

    public enum Alineacion { IZQUIERDA, DERECHA }

    public record Columna(String titulo, float peso, Alineacion alineacion) {
        public static Columna texto(String titulo, float peso) {
            return new Columna(titulo, peso, Alineacion.IZQUIERDA);
        }

        public static Columna numero(String titulo, float peso) {
            return new Columna(titulo, peso, Alineacion.DERECHA);
        }
    }

    /** Par etiqueta/valor; {@code ancho} ocupa toda la fila (textos largos). */
    public record Campo(String etiqueta, String valor, boolean ancho) {
        public static Campo de(String etiqueta, String valor) {
            return new Campo(etiqueta, valor, false);
        }

        public static Campo ancho(String etiqueta, String valor) {
            return new Campo(etiqueta, valor, true);
        }
    }

    private static final PDRectangle PAGINA = PDRectangle.A4;
    private static final float MARGEN = 40f;
    private static final float ANCHO = PAGINA.getWidth() - 2 * MARGEN;
    private static final float Y_CONTENIDO = PAGINA.getHeight() - 100f;
    private static final float Y_MINIMO = 56f;
    private static final int COLUMNAS_CAMPOS = 3;

    private static final Color MARINO = new Color(11, 58, 110);
    private static final Color TEXTO = new Color(30, 41, 59);
    private static final Color TENUE = new Color(100, 116, 139);
    private static final Color LINEA = new Color(203, 213, 225);
    private static final Color FONDO_CABECERA = new Color(232, 238, 246);

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<Integer, Boolean> codificable = new HashMap<>();

    private final PDDocument doc = new PDDocument();
    private final String titulo;
    private final String codigo;
    private final String empresa;
    private final String pie;
    private final PDImageXObject logo;
    private PDPageContentStream cs;
    private float y;

    /**
     * @param plantilla identificador y version de la plantilla (p. ej. "PLT-NI v1"),
     *                  para saber con que maqueta se genero un PDF ya impreso.
     */
    public PdfCorporativo(String titulo, String codigo, String empresaCode, String plantilla, String pie) {
        this.titulo = titulo;
        this.codigo = codigo;
        this.empresa = empresaCode;
        this.pie = plantilla + " · " + pie;
        this.logo = "TDEMSRL".equals(empresaCode) ? cargarLogo() : null;
        nuevaPagina();
    }

    // ---------------------------------------------------------------- contenido

    public void seccion(String texto) {
        asegurarEspacio(40f);
        y -= 14f;
        escribir(texto.toUpperCase(), negrita, 9.5f, MARGEN, y, MARINO);
        y -= 5f;
        linea(MARGEN, y, MARGEN + ANCHO, y, LINEA, 0.6f);
        y -= 12f;
    }

    public void campos(List<Campo> campos) {
        List<Campo> fila = new ArrayList<>();
        for (Campo c : campos) {
            if (c.ancho()) {
                filaCampos(fila);
                fila.clear();
                filaCampos(List.of(c));
            } else {
                fila.add(c);
                if (fila.size() == COLUMNAS_CAMPOS) {
                    filaCampos(fila);
                    fila.clear();
                }
            }
        }
        filaCampos(fila);
    }

    public void parrafo(String texto) {
        for (String linea : envolver(texto, normal, 9f, ANCHO)) {
            asegurarEspacio(12f);
            escribir(linea, normal, 9f, MARGEN, y, TEXTO);
            y -= 12f;
        }
        y -= 4f;
    }

    public void tabla(List<Columna> columnas, List<List<String>> filas) {
        float pesoTotal = (float) columnas.stream().mapToDouble(Columna::peso).sum();
        float[] anchos = new float[columnas.size()];
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = ANCHO * columnas.get(i).peso() / pesoTotal;
        }
        encabezadoTabla(columnas, anchos);
        if (filas.isEmpty()) {
            asegurarEspacio(16f);
            escribir("Sin registros.", normal, 8.5f, MARGEN + 4f, y - 10f, TENUE);
            y -= 18f;
            return;
        }
        for (List<String> fila : filas) {
            List<List<String>> celdas = new ArrayList<>();
            int lineas = 1;
            for (int i = 0; i < columnas.size(); i++) {
                List<String> l = envolver(i < fila.size() ? fila.get(i) : "", normal, 8.5f, anchos[i] - 8f);
                celdas.add(l);
                lineas = Math.max(lineas, l.size());
            }
            float alto = lineas * 10.5f + 6f;
            if (y - alto < Y_MINIMO) {
                nuevaPagina();
                encabezadoTabla(columnas, anchos);
            }
            float x = MARGEN;
            for (int i = 0; i < columnas.size(); i++) {
                float yl = y - 11f;
                for (String l : celdas.get(i)) {
                    escribirAlineado(l, normal, 8.5f, x, anchos[i], yl, columnas.get(i).alineacion(), TEXTO);
                    yl -= 10.5f;
                }
                x += anchos[i];
            }
            y -= alto;
            linea(MARGEN, y, MARGEN + ANCHO, y, LINEA, 0.4f);
        }
        y -= 6f;
    }

    /**
     * Recuadro con un QR a la izquierda y, a su lado, un titulo, un texto y la
     * URL (DEC-L052: enlace del proveedor en la RFQ). El QR se dibuja con
     * rectangulos, sin imagenes.
     */
    public void enlaceConQr(String tituloEnlace, String texto, String url) {
        com.google.zxing.common.BitMatrix m;
        try {
            m = new com.google.zxing.qrcode.QRCodeWriter().encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(com.google.zxing.EncodeHintType.MARGIN, 0,
                            com.google.zxing.EncodeHintType.ERROR_CORRECTION,
                            com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M));
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR.", e);
        }
        float lado = 88f;
        float modulo = lado / m.getWidth();
        float alto = lado + 16f;
        asegurarEspacio(alto + 8f);
        float top = y - 8f;
        float x0 = MARGEN + 8f;
        float y0 = top - lado;
        io(() -> {
            cs.setStrokingColor(LINEA);
            cs.setLineWidth(0.6f);
            cs.addRect(MARGEN, top - alto + 8f, ANCHO, alto);
            cs.stroke();
            cs.setNonStrokingColor(Color.BLACK);
            for (int fy = 0; fy < m.getHeight(); fy++) {
                for (int fx = 0; fx < m.getWidth(); fx++) {
                    if (m.get(fx, fy)) {
                        cs.addRect(x0 + fx * modulo, y0 + (m.getHeight() - 1 - fy) * modulo, modulo, modulo);
                    }
                }
            }
            cs.fill();
        });
        float xt = x0 + lado + 14f;
        float anchoTexto = MARGEN + ANCHO - xt - 8f;
        float yl = top - 12f;
        escribir(tituloEnlace, negrita, 10f, xt, yl, MARINO);
        yl -= 14f;
        for (String l : envolver(texto, normal, 8.5f, anchoTexto)) {
            escribir(l, normal, 8.5f, xt, yl, TEXTO);
            yl -= 11f;
        }
        yl -= 3f;
        for (String l : envolver(url, normal, 7.5f, anchoTexto)) {
            escribir(l, normal, 7.5f, xt, yl, MARINO);
            yl -= 9.5f;
        }
        y = top - alto;
    }

    /** Lineas etiqueta/valor alineadas a la derecha, bajo una tabla (totales). */
    public void totales(List<String[]> lineas) {
        for (String[] l : lineas) {
            asegurarEspacio(14f);
            float anchoValor = 90f;
            escribirAlineado(l[0], negrita, 9f, MARGEN, ANCHO - anchoValor, y - 10f, Alineacion.DERECHA, TEXTO);
            escribirAlineado(l[1], negrita, 9f, MARGEN + ANCHO - anchoValor, anchoValor, y - 10f, Alineacion.DERECHA, TEXTO);
            y -= 14f;
        }
        y -= 4f;
    }

    public byte[] terminar() {
        io(() -> cs.close());
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = doc.getPage(i);
            int numero = i + 1;
            io(() -> {
                try (PDPageContentStream pieCs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    cs = pieCs;
                    linea(MARGEN, 40f, MARGEN + ANCHO, 40f, LINEA, 0.5f);
                    escribir(recortar(pie, normal, 7f, ANCHO - 70f), normal, 7f, MARGEN, 28f, TENUE);
                    escribirAlineado("Página " + numero + " de " + total, normal, 7f, MARGEN, ANCHO, 28f, Alineacion.DERECHA, TENUE);
                }
            });
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            doc.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- maqueta

    private void nuevaPagina() {
        if (cs != null) {
            io(() -> cs.close());
        }
        PDPage page = new PDPage(PAGINA);
        doc.addPage(page);
        io(() -> cs = new PDPageContentStream(doc, page));
        float tope = PAGINA.getHeight() - MARGEN;
        if (logo != null) {
            float alto = 36f;
            float ancho = alto * logo.getWidth() / logo.getHeight();
            io(() -> cs.drawImage(logo, MARGEN, tope - alto, ancho, alto));
        } else {
            escribir(empresa, negrita, 18f, MARGEN, tope - 26f, MARINO);
        }
        float xDer = MARGEN;
        escribirAlineado(titulo, negrita, 13f, xDer, ANCHO, tope - 12f, Alineacion.DERECHA, MARINO);
        escribirAlineado(codigo, negrita, 10f, xDer, ANCHO, tope - 27f, Alineacion.DERECHA, TEXTO);
        escribirAlineado("Empresa: " + empresa, normal, 8f, xDer, ANCHO, tope - 39f, Alineacion.DERECHA, TENUE);
        linea(MARGEN, tope - 48f, MARGEN + ANCHO, tope - 48f, MARINO, 1.2f);
        y = Y_CONTENIDO;
    }

    private void asegurarEspacio(float alto) {
        if (y - alto < Y_MINIMO) {
            nuevaPagina();
        }
    }

    private void filaCampos(List<Campo> fila) {
        if (fila.isEmpty()) {
            return;
        }
        float anchoCol = fila.size() == 1 && fila.get(0).ancho() ? ANCHO : ANCHO / COLUMNAS_CAMPOS;
        List<List<String>> valores = new ArrayList<>();
        int lineas = 1;
        for (Campo c : fila) {
            List<String> l = envolver(vacioComoGuion(c.valor()), normal, 9f, anchoCol - 10f);
            valores.add(l);
            lineas = Math.max(lineas, l.size());
        }
        float alto = 11f + lineas * 11.5f + 6f;
        asegurarEspacio(alto);
        for (int i = 0; i < fila.size(); i++) {
            float x = MARGEN + i * anchoCol;
            escribir(fila.get(i).etiqueta().toUpperCase(), negrita, 6.5f, x, y - 7f, TENUE);
            float yl = y - 19f;
            for (String l : valores.get(i)) {
                escribir(l, normal, 9f, x, yl, TEXTO);
                yl -= 11.5f;
            }
        }
        y -= alto;
    }

    private void encabezadoTabla(List<Columna> columnas, float[] anchos) {
        List<List<String>> titulos = new ArrayList<>();
        int lineas = 1;
        for (int i = 0; i < columnas.size(); i++) {
            List<String> l = envolver(columnas.get(i).titulo().toUpperCase(), negrita, 7f, anchos[i] - 8f);
            titulos.add(l);
            lineas = Math.max(lineas, l.size());
        }
        float alto = lineas * 9f + 8f;
        asegurarEspacio(alto + 20f);
        float altoFinal = alto;
        io(() -> {
            cs.setNonStrokingColor(FONDO_CABECERA);
            cs.addRect(MARGEN, y - altoFinal, ANCHO, altoFinal);
            cs.fill();
        });
        float x = MARGEN;
        for (int i = 0; i < columnas.size(); i++) {
            float yl = y - 11f;
            for (String l : titulos.get(i)) {
                escribirAlineado(l, negrita, 7f, x, anchos[i], yl, columnas.get(i).alineacion(), MARINO);
                yl -= 9f;
            }
            x += anchos[i];
        }
        y -= alto;
    }

    // ---------------------------------------------------------------- texto

    private void escribirAlineado(String texto, PDFont f, float size, float x, float ancho, float yl,
                                  Alineacion al, Color color) {
        String t = limpiar(texto, f);
        float xt = al == Alineacion.DERECHA ? x + ancho - 4f - anchoTexto(t, f, size) : x + 4f;
        escribirLimpio(t, f, size, xt, yl, color);
    }

    private void escribir(String texto, PDFont f, float size, float x, float yl, Color color) {
        escribirLimpio(limpiar(texto, f), f, size, x, yl, color);
    }

    private void escribirLimpio(String t, PDFont f, float size, float x, float yl, Color color) {
        if (t.isEmpty()) {
            return;
        }
        io(() -> {
            cs.beginText();
            cs.setFont(f, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, yl);
            cs.showText(t);
            cs.endText();
        });
    }

    private void linea(float x1, float y1, float x2, float y2, Color color, float grosor) {
        io(() -> {
            cs.setStrokingColor(color);
            cs.setLineWidth(grosor);
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        });
    }

    /** Parte el texto en lineas que caben en {@code ancho}; respeta saltos de linea y corta palabras largas. */
    private List<String> envolver(String texto, PDFont f, float size, float ancho) {
        List<String> lineas = new ArrayList<>();
        for (String parrafo : limpiar(texto, f).split("\n", -1)) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.trim().split("\\s+")) {
                if (palabra.isEmpty()) {
                    continue;
                }
                String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (anchoTexto(candidata, f, size) <= ancho) {
                    actual = new StringBuilder(candidata);
                    continue;
                }
                if (!actual.isEmpty()) {
                    lineas.add(actual.toString());
                }
                actual = new StringBuilder(palabra);
                while (anchoTexto(actual.toString(), f, size) > ancho && actual.length() > 1) {
                    int corte = actual.length() - 1;
                    while (corte > 1 && anchoTexto(actual.substring(0, corte), f, size) > ancho) {
                        corte--;
                    }
                    lineas.add(actual.substring(0, corte));
                    actual = new StringBuilder(actual.substring(corte));
                }
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }

    private String recortar(String texto, PDFont f, float size, float ancho) {
        String t = limpiar(texto, f);
        if (anchoTexto(t, f, size) <= ancho) {
            return t;
        }
        while (t.length() > 1 && anchoTexto(t + "...", f, size) > ancho) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    private float anchoTexto(String t, PDFont f, float size) {
        try {
            return f.getStringWidth(t) / 1000f * size;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deja solo caracteres que Helvetica (WinAnsi) puede dibujar. */
    private String limpiar(String texto, PDFont f) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length());
        texto.replace("\r", "").replace('\t', ' ').codePoints().forEach(cp -> {
            if (cp == '\n') {
                sb.append('\n');
            } else if (codificable.computeIfAbsent(cp, c -> puedeCodificar(c, f))) {
                sb.appendCodePoint(cp);
            } else if (cp == 0x2192) {
                sb.append("->");
            } else {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    private static boolean puedeCodificar(int cp, PDFont f) {
        try {
            f.encode(new String(Character.toChars(cp)));
            return true;
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private static String vacioComoGuion(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private PDImageXObject cargarLogo() {
        try (InputStream in = PdfCorporativo.class.getResourceAsStream("/documentos/logo-tdem.png")) {
            if (in == null) {
                return null;
            }
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "logo-tdem");
        } catch (IOException e) {
            return null;
        }
    }

    private interface AccionIo {
        void ejecutar() throws IOException;
    }

    private static void io(AccionIo accion) {
        try {
            accion.ejecutar();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
