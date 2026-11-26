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
 * Orden de compra con el formato F-LOG-09 v1 (DEC-L079), igual al formato del
 * sistema de gestion: cabecera con logo, codigo/version/fecha de aprobacion del
 * formato, datos de la empresa, proveedor y condiciones, detalle, VALOR TOTAL /
 * IGV / PRECIO TOTAL, cuentas del proveedor, especificaciones, condiciones
 * adicionales y los V°B° de Gerencia y Logistica.
 *
 * Las firmas escaneadas solo se estampan si la orden esta aprobada (las recibe
 * null en otro caso); una orden por aprobar o anulada lo dice en el documento.
 */
final class PdfOrdenCompra {

    /** Todo lo que se dibuja; lo arma {@link DocumentosService} con la autorizacion ya validada. */
    record Datos(
            String codigoOrden,
            String estado,
            boolean aprobada,
            String empresaNombreCorto,
            String empresaRuc,
            String empresaDireccion,
            String empresaTelefono,
            byte[] logo,
            String proveedor,
            String proveedorRuc,
            String fechaEmision,
            String plazoEntrega,
            String contacto,
            String telefonoContacto,
            String lugarEntrega,
            String formaPago,
            String moneda,
            List<Linea> lineas,
            String valorTotal,
            String etiquetaIgv,
            String igv,
            String precioTotal,
            String notaIgv,
            List<String> cuentasDeposito,
            List<String> cuentasDetraccion,
            String especificaciones,
            byte[] firmaGerencia,
            byte[] firmaLogistica,
            String pie) {
    }

    record Linea(String codigo, String descripcion, String um, String cantidad, String valorUnitario,
                 String subtotal) {
    }

    static final String CODIGO_FORMATO = "F-LOG-09";
    static final String VERSION_FORMATO = "1";
    static final String FECHA_APROBACION_FORMATO = "29/01/2024";

    private static final Logger log = LoggerFactory.getLogger(PdfOrdenCompra.class);

    private static final PDRectangle PAGINA = PDRectangle.A4;
    private static final float X0 = 36f;
    private static final float W = PAGINA.getWidth() - 2 * X0;
    private static final float Y_MIN = 60f;
    private static final float TOPE = PAGINA.getHeight() - 30f;

    /** Anchos del detalle: CODIGO, DESCRIPCION, UM, CANTIDAD, VALOR UNITARIO, SUBTOTAL. */
    private static final float[] COLS = {70f, 200f, 45f, 60f, 75f, 73f};

    private static final Color NEGRO = Color.BLACK;
    private static final Color TENUE = new Color(110, 110, 110);
    private static final Color ROJO = new Color(185, 28, 28);
    private static final Color GRIS = new Color(225, 225, 225);

    private final PDFont courierBold = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
    private final PDFont courierItalica = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD_OBLIQUE);
    private final PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont helveticaBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<String, Boolean> codificable = new HashMap<>();

    private final PDDocument doc = new PDDocument();
    private final Datos d;
    private final PDImageXObject logo;
    private PDPageContentStream cs;
    private float y;

    private PdfOrdenCompra(Datos d) {
        this.d = d;
        this.logo = imagen(d.logo(), "logo");
    }

    static byte[] generar(Datos d) {
        return new PdfOrdenCompra(d).dibujar();
    }

    private byte[] dibujar() {
        nuevaPagina();
        titulo();
        datosGenerales();
        detalle();
        totales();
        recuadro("CUENTAS DE DEPÓSITO:", d.cuentasDeposito());
        recuadro("CUENTA DE DETRACCIÓN:", d.cuentasDetraccion());
        y -= 6f;
        recuadro("ESPECIFICACIONES ADICIONALES:", d.especificaciones() == null ? List.of()
                : List.of(d.especificaciones()));
        condiciones();
        firmas();
        return terminar();
    }

    // ------------------------------------------------------------------ secciones

    private void cabecera() {
        float alto = 58f;
        float base = TOPE - alto;
        float wLogo = 150f;
        float wDerecha = 150f;
        float wMedio = W - wLogo - wDerecha;
        float xMedio = X0 + wLogo;
        float xDerecha = xMedio + wMedio;
        rect(X0, base, W, alto);
        linea(xMedio, base, xMedio, TOPE);
        linea(xDerecha, base, xDerecha, TOPE);

        if (logo != null) {
            float maxW = wLogo - 12f;
            float maxH = alto - 12f;
            float escala = Math.min(maxW / logo.getWidth(), maxH / logo.getHeight());
            float lw = logo.getWidth() * escala;
            float lh = logo.getHeight() * escala;
            float lx = X0 + (wLogo - lw) / 2;
            float ly = base + (alto - lh) / 2;
            io(() -> cs.drawImage(logo, lx, ly, lw, lh));
        } else {
            centrado(d.empresaNombreCorto(), helveticaBold, 14f, X0, wLogo, base + alto / 2 - 5f, NEGRO);
        }

        float filaFormato = 14f;
        linea(xMedio, TOPE - filaFormato, xDerecha, TOPE - filaFormato);
        centrado("FORMATO", helveticaBold, 7.5f, xMedio, wMedio, TOPE - filaFormato + 4f, NEGRO);
        centrado("ORDEN DE COMPRA", helveticaBold, 8.5f, xMedio, wMedio, base + (alto - filaFormato) / 2 - 3f, NEGRO);

        float wEtiqueta = 75f;
        linea(xDerecha + wEtiqueta, base, xDerecha + wEtiqueta, TOPE);
        float[] filas = {14f, 14f, 30f};
        String[][] textos = {{"CÓDIGO:", CODIGO_FORMATO}, {"VERSIÓN:", VERSION_FORMATO},
                {"FECHA\nAPROBACIÓN:", FECHA_APROBACION_FORMATO}};
        float tope = TOPE;
        for (int i = 0; i < filas.length; i++) {
            float abajo = tope - filas[i];
            if (i < filas.length - 1) {
                linea(xDerecha, abajo, X0 + W, abajo);
            }
            String[] partes = textos[i][0].split("\n");
            float yTexto = abajo + filas[i] / 2 + (partes.length - 1) * 4.5f - 2.5f;
            for (String p : partes) {
                centrado(p, helveticaBold, 7.5f, xDerecha, wEtiqueta, yTexto, NEGRO);
                yTexto -= 9f;
            }
            centrado(textos[i][1], helvetica, 7.5f, xDerecha + wEtiqueta, wDerecha - wEtiqueta,
                    abajo + filas[i] / 2 - 2.5f, NEGRO);
            tope = abajo;
        }

        String datosEmpresa = "RUC: " + guion(d.empresaRuc()) + " - Dirección: " + guion(d.empresaDireccion())
                + " - Teléfono :" + guion(d.empresaTelefono());
        float yDatos = base - 9f;
        for (String l : envolver(datosEmpresa, helvetica, 6f, W - 4f)) {
            escribir(l, helvetica, 6f, X0 + 2f, yDatos, NEGRO);
            yDatos -= 7.5f;
        }
        linea(X0, yDatos + 4f, X0, base);
        y = yDatos - 6f;
    }

    private void titulo() {
        y -= 6f;
        centrado("ORDEN DE COMPRA: " + d.codigoOrden(), courierBold, 11f, X0, W, y, NEGRO);
        y -= 12f;
        String aviso = switch (d.estado()) {
            case "ANULADA" -> "ORDEN ANULADA - SIN VALIDEZ";
            case "POR_APROBAR" -> "BORRADOR - PENDIENTE DE APROBACIÓN";
            default -> null;
        };
        if (aviso != null) {
            centrado(aviso, helveticaBold, 8.5f, X0, W, y, "ANULADA".equals(d.estado()) ? ROJO : TENUE);
            y -= 12f;
        }
        y -= 2f;
    }

    private void datosGenerales() {
        float[] etiquetas = {72f, 66f, 84f};
        float[] valores = {118f, 108f, 75f};
        String[][][] filas = {
                {{"PROVEEDOR:", d.proveedor()}, {"RUC:", d.proveedorRuc()}, {"FECHA DE EMISIÓN:", d.fechaEmision()}},
                {{"PLAZO DE ENTREGA:", d.plazoEntrega()}, {"CONTACTO:", d.contacto()},
                        {"TELEFONO:", d.telefonoContacto()}},
                {{"LUGAR DE ENTREGA:", d.lugarEntrega()}, {"FORMA DE PAGO:", d.formaPago()}, {"MONEDA:", d.moneda()}}};
        for (String[][] fila : filas) {
            List<List<String>> lineasEtiqueta = new ArrayList<>();
            List<List<String>> lineasValor = new ArrayList<>();
            int max = 1;
            for (int i = 0; i < fila.length; i++) {
                List<String> e = envolver(fila[i][0], courierBold, 7.5f, etiquetas[i] - 6f);
                List<String> v = recortar(envolver(guion(fila[i][1]), helvetica, 7.5f, valores[i] - 6f), 3);
                lineasEtiqueta.add(e);
                lineasValor.add(v);
                max = Math.max(max, Math.max(e.size(), v.size()));
            }
            float alto = max * 9f + 5f;
            float x = X0;
            for (int i = 0; i < fila.length; i++) {
                float yl = y - 8f;
                for (String l : lineasEtiqueta.get(i)) {
                    escribir(l, courierBold, 7.5f, x + 4f, yl, NEGRO);
                    yl -= 9f;
                }
                float yv = y - 8f;
                for (String l : lineasValor.get(i)) {
                    escribir(l, helvetica, 7.5f, x + etiquetas[i], yv, NEGRO);
                    yv -= 9f;
                }
                x += etiquetas[i] + valores[i];
            }
            y -= alto;
        }
        y -= 6f;
    }

    private void detalle() {
        encabezadoDetalle();
        for (Linea l : d.lineas()) {
            List<String> desc = envolver(guion(l.descripcion()), helvetica, 7.5f, COLS[1] - 6f);
            List<String> codigo = envolver(guion(l.codigo()), helvetica, 7f, COLS[0] - 6f);
            int n = Math.max(desc.size(), codigo.size());
            float alto = n * 9f + 5f;
            if (y - alto < Y_MIN) {
                nuevaPagina();
                encabezadoDetalle();
            }
            float abajo = y - alto;
            celdas(abajo, alto);
            float yl = y - 9f;
            for (int i = 0; i < n; i++) {
                if (i < codigo.size()) {
                    escribir(codigo.get(i), helvetica, 7f, X0 + 3f, yl, NEGRO);
                }
                if (i < desc.size()) {
                    escribir(desc.get(i), helvetica, 7.5f, X0 + COLS[0] + 3f, yl, NEGRO);
                }
                yl -= 9f;
            }
            float yFila = y - 9f;
            float x = X0 + COLS[0] + COLS[1];
            centrado(guion(l.um()), helvetica, 7.5f, x, COLS[2], yFila, NEGRO);
            x += COLS[2];
            derecha(l.cantidad(), helvetica, 7.5f, x, COLS[3], yFila);
            x += COLS[3];
            derecha(l.valorUnitario(), helvetica, 7.5f, x, COLS[4], yFila);
            x += COLS[4];
            derecha(l.subtotal(), helvetica, 7.5f, x, COLS[5], yFila);
            y = abajo;
        }
    }

    private void encabezadoDetalle() {
        float alto = 13f;
        String[] titulos = {"CÓDIGO", "DESCRIPCIÓN DEL PRODUCTO", "UM", "CANTIDAD", "VALOR UNITARIO", "SUBTOTAL"};
        celdas(y - alto, alto);
        float x = X0;
        for (int i = 0; i < titulos.length; i++) {
            if (i == 1) {
                escribir(titulos[i], courierBold, 7.5f, x + 3f, y - 9f, NEGRO);
            } else {
                centrado(titulos[i], courierBold, 7.5f, x, COLS[i], y - 9f, NEGRO);
            }
            x += COLS[i];
        }
        y -= alto;
    }

    private void totales() {
        float alto = 12f;
        if (y - 3 * alto - 4f < Y_MIN) {
            nuevaPagina();
        }
        float xEtiqueta = X0 + COLS[0] + COLS[1] + COLS[2];
        float xMedio = xEtiqueta + COLS[3];
        float xValor = xMedio + COLS[4];
        String[][] filas = {{"VALOR TOTAL", "", d.valorTotal()}, {"IGV", d.etiquetaIgv(), d.igv()},
                {"PRECIO TOTAL", "", d.precioTotal()}};
        for (int i = 0; i < filas.length; i++) {
            float abajo = y - alto;
            if (i == filas.length - 1) {
                relleno(xValor, abajo, COLS[5], alto, GRIS);
            }
            rect(xEtiqueta, abajo, COLS[3], alto);
            rect(xMedio, abajo, COLS[4], alto);
            rect(xValor, abajo, COLS[5], alto);
            centrado(filas[i][0], courierBold, 7.5f, xEtiqueta, COLS[3], abajo + 3.5f, NEGRO);
            centrado(filas[i][1], helvetica, 7.5f, xMedio, COLS[4], abajo + 3.5f, TENUE);
            derecha(filas[i][2], i == filas.length - 1 ? helveticaBold : helvetica, 7.5f, xValor, COLS[5],
                    abajo + 3.5f);
            y = abajo;
        }
        if (d.notaIgv() != null) {
            escribir(d.notaIgv(), helvetica, 7f, X0 + 2f, y + 3.5f, TENUE);
        }
        y -= 8f;
    }

    /** Recuadro con titulo a la izquierda y lineas de texto; crece con el contenido. */
    private void recuadro(String titulo, List<String> textos) {
        List<String> lineas = new ArrayList<>();
        for (String t : textos) {
            lineas.addAll(envolver(t, helvetica, 7.5f, W - 8f));
        }
        float alto = Math.max(24f, 15f + lineas.size() * 9f + 3f);
        if (y - alto < Y_MIN) {
            nuevaPagina();
        }
        rect(X0, y - alto, W, alto);
        escribir(titulo, courierBold, 7.5f, X0 + 2f, y - 9f, NEGRO);
        float yl = y - 19f;
        for (String l : lineas) {
            escribir(l, helvetica, 7.5f, X0 + 4f, yl, NEGRO);
            yl -= 9f;
        }
        y -= alto;
    }

    private void condiciones() {
        String empresa = d.empresaNombreCorto();
        String texto = "Condiciones adicionales a la compra o servicio:\n"
                + "1. Mediante el presente potencial acuerdo contractual, el proveedor y/o locador de servicios autoriza "
                + "libre y expresamente los datos personales suministrados, los mismos que serán tratados por " + empresa
                + " y se utilizarán para finalidades relacionadas con su relación laboral. Sírvase remitir, la "
                + "mercadería señalada adjuntando esta orden, la factura y/o Guía correspondiente.\n"
                + "2. Se establece que las empresas proveedoras del mantenimiento vehicular son responsables de la "
                + "disposición final apropiada y segura de los residuos generados durante el servicio.";
        List<String> lineas = envolver(texto, courierItalica, 7.2f, W);
        if (y - lineas.size() * 8.5f - 8f < Y_MIN) {
            nuevaPagina();
        }
        y -= 10f;
        for (String l : lineas) {
            escribir(l, courierItalica, 7.2f, X0, y, NEGRO);
            y -= 8.5f;
        }
    }

    private void firmas() {
        float alto = 105f;
        if (y - alto < Y_MIN) {
            nuevaPagina();
        }
        float ancho = 150f;
        float base = y - 75f;
        firma(X0 + W * 0.25f - ancho / 2, base, ancho, d.firmaGerencia(), "V° B° Gerencia", "Gerente General");
        firma(X0 + W * 0.75f - ancho / 2, base, ancho, d.firmaLogistica(), "V° B° Logística", "Gestor de Compras");
        y = base - 26f;
    }

    private void firma(float x, float base, float ancho, byte[] imagen, String vb, String cargo) {
        PDImageXObject img = imagen(imagen, "firma");
        float altoMax = 62f;
        if (img != null) {
            float escala = Math.min(ancho / img.getWidth(), altoMax / img.getHeight());
            float fw = img.getWidth() * escala;
            float fh = img.getHeight() * escala;
            io(() -> cs.drawImage(img, x + (ancho - fw) / 2, base + 2f, fw, fh));
        } else if (!d.aprobada()) {
            String aviso = "ANULADA".equals(d.estado()) ? "Anulada" : "Pendiente de aprobación";
            centrado(aviso, helvetica, 7.5f, x, ancho, base + 24f, TENUE);
        }
        linea(x, base, x + ancho, base);
        centrado(vb, helveticaBold, 7.5f, x, ancho, base - 10f, NEGRO);
        centrado(cargo, helvetica, 7.5f, x, ancho, base - 20f, NEGRO);
    }

    // ------------------------------------------------------------------ paginas

    private void nuevaPagina() {
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

    private void cerrarPagina() {
        if (cs != null) {
            io(() -> cs.close());
            cs = null;
        }
    }

    private byte[] terminar() {
        cerrarPagina();
        int total = doc.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage pagina = doc.getPage(i);
            String numero = "Página " + (i + 1) + " de " + total;
            try (PDPageContentStream pie = new PDPageContentStream(doc, pagina,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs = pie;
                if (d.pie() != null && !d.pie().isBlank()) {
                    escribir(d.pie(), helvetica, 6f, X0, 24f, TENUE);
                }
                derecha(numero, helvetica, 7f, X0, W, 24f, TENUE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        cs = null;
        try (doc; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ primitivas

    /** Celdas con borde de una fila del detalle. */
    private void celdas(float abajo, float alto) {
        float x = X0;
        for (float c : COLS) {
            rect(x, abajo, c, alto);
            x += c;
        }
    }

    private void rect(float x, float yAbajo, float w, float h) {
        io(() -> {
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.6f);
            cs.addRect(x, yAbajo, w, h);
            cs.stroke();
        });
    }

    private void relleno(float x, float yAbajo, float w, float h, Color color) {
        io(() -> {
            cs.setNonStrokingColor(color);
            cs.addRect(x, yAbajo, w, h);
            cs.fill();
        });
    }

    private void linea(float x1, float y1, float x2, float y2) {
        io(() -> {
            cs.setStrokingColor(NEGRO);
            cs.setLineWidth(0.6f);
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        });
    }

    private void escribir(String texto, PDFont f, float tam, float x, float yBase, Color color) {
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

    private void centrado(String texto, PDFont f, float tam, float x, float ancho, float yBase, Color color) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        float w = ancho(seguro(texto, f), f, tam);
        escribir(texto, f, tam, x + (ancho - w) / 2, yBase, color);
    }

    private void derecha(String texto, PDFont f, float tam, float x, float ancho, float yBase) {
        derecha(texto, f, tam, x, ancho, yBase, NEGRO);
    }

    private void derecha(String texto, PDFont f, float tam, float x, float ancho, float yBase, Color color) {
        if (texto == null || texto.isEmpty()) {
            return;
        }
        float w = ancho(seguro(texto, f), f, tam);
        escribir(texto, f, tam, x + ancho - w - 3f, yBase, color);
    }

    /** Parte el texto en lineas que caben en {@code ancho}; respeta los saltos de linea. */
    private List<String> envolver(String texto, PDFont f, float tam, float ancho) {
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
                // Palabra mas ancha que la columna (codigos largos): se corta por caracteres.
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

    private static List<String> recortar(List<String> lineas, int max) {
        if (lineas.size() <= max) {
            return lineas;
        }
        List<String> r = new ArrayList<>(lineas.subList(0, max));
        r.set(max - 1, r.get(max - 1) + "…");
        return r;
    }

    private float ancho(String texto, PDFont f, float tam) {
        try {
            return f.getStringWidth(texto) / 1000f * tam;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Helvetica/Courier estandar solo cubren WinAnsi: lo demas se reemplaza. */
    private String seguro(String texto, PDFont f) {
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

    private PDImageXObject imagen(byte[] bytes, String nombre) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return PDImageXObject.createFromByteArray(doc, bytes, nombre);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Imagen '{}' de la orden de compra no se pudo leer: {}", nombre, e.getMessage());
            return null;
        }
    }

    private static String guion(String s) {
        return s == null || s.isBlank() ? "-" : s;
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
