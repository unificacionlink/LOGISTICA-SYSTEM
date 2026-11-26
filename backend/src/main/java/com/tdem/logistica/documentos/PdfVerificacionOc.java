package com.tdem.logistica.documentos;

import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.util.ArrayList;
import java.util.List;

/**
 * F-LOG-08 v1 "Verificacion de adquisiciones por orden de compra" (DEC-L102), igual
 * al formato del sistema de gestion: datos de la OC y su proveedor, el documento
 * adjunto (la propia OC), una fila por producto con lo pedido, lo recibido y las
 * tres verificaciones Y/F, la conclusion y los V°B° de Logistica y del area
 * solicitante o especialista con la firma de cada uno.
 */
final class PdfVerificacionOc extends HojaPdf {

    record Datos(
            String empresaNombreCorto,
            byte[] logo,
            String codigoOrden,
            String proveedor,
            String telefono,
            String ruc,
            String codigoDocumento,
            String lugarEntrega,
            String fechaVerificacion,
            String enlaceDocumento,
            List<Linea> lineas,
            String conclusion,
            Firma logistica,
            Firma solicitante) {
    }

    /** Y = conforme, F = no conforme, null = no aplica (queda en blanco). */
    record Linea(String descripcion, String um, String cantidad, String recibida, String funcionalidad,
                 String estandares, String especificaciones, String observacion) {
    }

    record Firma(String nombre, String cargo, byte[] imagen) {
    }

    static final String CODIGO_FORMATO = "F-LOG-08";
    static final String VERSION_FORMATO = "1";
    static final String FECHA_APROBACION_FORMATO = "29/01/2025";

    /** DESCRIPCION, UM, CANTIDAD, RECIBIDA, FUNCIONALIDAD, ESTANDARES, ESPECIFICACIONES, OBSERVACION. */
    private static final float[] COLS = {118f, 30f, 44f, 44f, 72f, 82f, 70f, 63f};
    private static final String[] TITULOS = {
            "DESCRIPCIÓN DEL PRODUCTO", "UM", "CANTIDAD", "CANTIDAD RECIBIDA",
            "¿APRUEBA FUNCIONALIDAD POR TESTEO* Y/O MUESTREO*?",
            "¿CUMPLE CON ESTÁNDARES Y/O NORMAS DE LA LISTA DE PRODUCTOS CRÍTICOS F-LOG-4?",
            "¿CUMPLE CON LAS ESPECIFICACIONES DE LA ORDEN DE COMPRA Y ESTÁ EN CONDICIONES ÓPTIMAS?",
            "OBSERVACIÓN"};
    /** Aclaracion en negrita bajo el titulo de las columnas 4 y 5. */
    private static final String[] NOTAS = {null, null, null, null, "*SOLO PARA EQUIPOS ELECTRÓNICOS*",
            "*SOLO PRODUCTOS CRÍTICOS*", null, null};

    private final Datos d;
    private final PDImageXObject logo;

    private PdfVerificacionOc(Datos d) {
        this.d = d;
        this.logo = imagen(d.logo(), "logo");
    }

    static byte[] generar(Datos d) {
        return new PdfVerificacionOc(d).dibujar();
    }

    @Override
    protected void cabecera() {
        cabeceraFormato(logo, d.empresaNombreCorto(), "VERIFICACIÓN DE ADQUISICIONES POR ORDEN DE COMPRA",
                CODIGO_FORMATO, VERSION_FORMATO, FECHA_APROBACION_FORMATO);
    }

    private byte[] dibujar() {
        nuevaPagina();
        titulo();
        datosGenerales();
        detalle();
        conclusion();
        firmas();
        return terminar();
    }

    private void titulo() {
        y -= 14f;
        String etiqueta = "ORDEN DE COMPRA A VERIFICAR: ";
        float wEtiqueta = ancho(seguro(etiqueta, courierBold), courierBold, 10f);
        float wCodigo = ancho(seguro(d.codigoOrden(), courierBold), courierBold, 10f);
        float x = X0 + (W - wEtiqueta - wCodigo) / 2;
        escribir(etiqueta, courierBold, 10f, x, y, NEGRO);
        escribir(d.codigoOrden(), courierBold, 10f, x + wEtiqueta, y, ROJO);
        y -= 18f;
    }

    private void datosGenerales() {
        float wEtiqueta1 = 105f;
        float wValor1 = 155f;
        float wEtiqueta2 = 120f;
        float wValor2 = W - wEtiqueta1 - wValor1 - wEtiqueta2;
        String[][] filas = {
                {"PROVEEDOR:", d.proveedor(), "TELEFONO:", d.telefono()},
                {"RUC:", d.ruc(), "CÓDIGO DEL\nDOCUMENTO ADJUNTO:", d.codigoDocumento()},
                {"LUGAR DE ENTREGA:", d.lugarEntrega(), "FECHA DE\nVERIFICACIÓN:", d.fechaVerificacion()}};
        for (String[] f : filas) {
            List<String> v1 = envolver(guion(f[1]), courier, 7.5f, wValor1 - 4f);
            List<String> e2 = List.of(f[2].split("\n"));
            List<String> v2 = envolver(guion(f[3]), courier, 7.5f, wValor2 - 4f);
            int n = Math.max(Math.max(v1.size(), e2.size()), v2.size());
            float alto = n * 8.5f + 6f;
            asegurar(alto);
            escribir(f[0], courierBold, 7.5f, X0, y - 8f, NEGRO);
            float yl = y - 8f;
            for (String l : v1) {
                escribir(l, courier, 7.5f, X0 + wEtiqueta1, yl, NEGRO);
                yl -= 8.5f;
            }
            float xE2 = X0 + wEtiqueta1 + wValor1;
            yl = y - 8f;
            for (String l : e2) {
                centrado(l, courierBold, 7.5f, xE2, wEtiqueta2, yl, NEGRO);
                yl -= 8.5f;
            }
            yl = y - 8f - (e2.size() - 1) * 4.25f;
            for (String l : v2) {
                centrado(l, courier, 7.5f, xE2 + wEtiqueta2, wValor2, yl, NEGRO);
                yl -= 8.5f;
            }
            y -= alto;
        }
        List<String> enlace = envolver(guion(d.enlaceDocumento()), courier, 7.5f, W - wEtiqueta1 - 4f);
        asegurar(enlace.size() * 8.5f + 6f);
        escribir("LINK DEL DOCUMENTO ADJUNTO", courierBold, 7f, X0, y - 8f, NEGRO);
        float yl = y - 8f;
        for (String l : enlace) {
            escribir(l, courier, 7.5f, X0 + wEtiqueta1 + 15f, yl, NEGRO);
            yl -= 8.5f;
        }
        y -= enlace.size() * 8.5f + 18f;
    }

    private void detalle() {
        asegurar(110f);
        derecha("Marca con \"Y\" los conformes y con \"F\" los no conformes.", courierBold, 7.5f, X0, W, y, NEGRO);
        y -= 6f;
        encabezadoDetalle();
        for (Linea l : d.lineas()) {
            String[] textos = {l.descripcion(), l.um(), l.cantidad(), l.recibida(), l.funcionalidad(),
                    l.estandares(), l.especificaciones(), l.observacion()};
            List<List<String>> celdas = new ArrayList<>();
            int n = 1;
            for (int i = 0; i < textos.length; i++) {
                List<String> c = envolver(textos[i] == null ? "" : textos[i], courier, 7f, COLS[i] - 6f);
                celdas.add(c);
                n = Math.max(n, c.size());
            }
            float alto = n * 8.5f + 8f;
            if (y - alto < Y_MIN) {
                nuevaPagina();
                encabezadoDetalle();
            }
            float abajo = y - alto;
            float x = X0;
            for (int i = 0; i < COLS.length; i++) {
                rect(x, abajo, COLS[i], alto);
                float yl = abajo + alto / 2 + (celdas.get(i).size() - 1) * 4.25f - 2.5f;
                for (String linea : celdas.get(i)) {
                    centrado(linea, courier, 7f, x, COLS[i], yl, NEGRO);
                    yl -= 8.5f;
                }
                x += COLS[i];
            }
            y = abajo;
        }
        List<String> nota = envolver("**Para verificar si lo adquirido cumple con Estandares, Certificaciones o Normas, "
                + "revisar fichas tecnicas adjuntas, identificar relieves y/o etiquetas en el mismo de acuerdo a lo "
                + "indicado en el F-LOG-04.", courierBold, 5.5f, W);
        y -= 7f;
        for (String l : nota) {
            escribir(l, courierBold, 5.5f, X0, y, NEGRO);
            y -= 6.5f;
        }
        y -= 14f;
    }

    private void encabezadoDetalle() {
        List<List<String>> titulos = new ArrayList<>();
        List<List<String>> notas = new ArrayList<>();
        int n = 1;
        for (int i = 0; i < TITULOS.length; i++) {
            List<String> t = envolver(TITULOS[i], courier, 6.2f, COLS[i] - 4f);
            List<String> nn = NOTAS[i] == null ? List.of() : envolver(NOTAS[i], courierBold, 6.2f, COLS[i] - 4f);
            titulos.add(t);
            notas.add(nn);
            n = Math.max(n, t.size() + nn.size());
        }
        float alto = n * 7f + 10f;
        asegurar(alto + 20f);
        float abajo = y - alto;
        float x = X0;
        for (int i = 0; i < COLS.length; i++) {
            rect(x, abajo, COLS[i], alto);
            int lineas = titulos.get(i).size() + notas.get(i).size();
            float yl = abajo + alto / 2 + (lineas - 1) * 3.5f - 2f;
            for (String l : titulos.get(i)) {
                centrado(l, courier, 6.2f, x, COLS[i], yl, NEGRO);
                yl -= 7f;
            }
            for (String l : notas.get(i)) {
                centrado(l, courierBold, 6.2f, x, COLS[i], yl, NEGRO);
                yl -= 7f;
            }
            x += COLS[i];
        }
        y = abajo;
    }

    private void conclusion() {
        List<String> lineas = envolver(guion(d.conclusion()), courier, 7.5f, W - 8f);
        float alto = Math.max(18f, lineas.size() * 9f + 8f);
        asegurar(alto + 20f);
        escribir("OBSERVACIONES Y/O CONCLUSIONES DE LA VERIFICACIÓN", courierBold, 7.5f, X0, y, NEGRO);
        y -= 6f;
        rect(X0, y - alto, W, alto);
        float yl = y - 11f;
        for (String l : lineas) {
            escribir(l, courier, 7.5f, X0 + 3f, yl, NEGRO);
            yl -= 9f;
        }
        y -= alto + 14f;
    }

    private void firmas() {
        float ancho = 175f;
        float alto = 130f;
        asegurar(alto + 10f);
        firma(X0 + W * 0.25f - ancho / 2, ancho, d.logistica(), "V°B° ÁREA LOGÍSTICA");
        firma(X0 + W * 0.75f - ancho / 2, ancho, d.solicitante(), "V°B° ÁREA SOLICITANTE O\nESPECIALISTA");
        y -= alto;
    }

    private void firma(float x, float ancho, Firma f, String vb) {
        float altoFirma = 105f;
        float altoVb = 24f;
        float arriba = y;
        float base = arriba - altoFirma;
        rect(x, base, ancho, altoFirma);
        rect(x, base - altoVb, ancho, altoVb);
        PDImageXObject img = f == null ? null : imagen(f.imagen(), "firma");
        if (img != null) {
            float maxW = ancho - 20f;
            float maxH = 58f;
            float escala = Math.min(maxW / img.getWidth(), maxH / img.getHeight());
            float fw = img.getWidth() * escala;
            float fh = img.getHeight() * escala;
            io(() -> cs.drawImage(img, x + (ancho - fw) / 2, base + 34f, fw, fh));
        }
        if (f != null) {
            centrado(f.nombre() == null ? "" : f.nombre().toUpperCase(), helveticaBold, 8f, x, ancho, base + 20f, NEGRO);
            centrado(f.cargo() == null ? "" : f.cargo().toUpperCase(), helveticaBold, 5.5f, x, ancho, base + 11f, NEGRO);
        }
        String[] partes = vb.split("\n");
        float yl = base - altoVb / 2 + (partes.length - 1) * 4f - 2.5f;
        for (String p : partes) {
            centrado(p, courier, 7f, x, ancho, yl, NEGRO);
            yl -= 8f;
        }
    }
}
