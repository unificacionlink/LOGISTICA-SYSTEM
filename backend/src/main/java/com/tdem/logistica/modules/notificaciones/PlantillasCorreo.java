package com.tdem.logistica.modules.notificaciones;

import java.util.Map;

/**
 * HTML de los correos (DEC-L066): tablas con estilos en linea, como el de RR.HH.,
 * porque los clientes de correo ignoran las hojas de estilo. Todo texto que
 * viene de los usuarios se escapa.
 */
final class PlantillasCorreo {

    private static final String AZUL = "#0b3a6e";
    private static final String APP = "Logística TDEM";

    private PlantillasCorreo() {
    }

    /**
     * @param parrafo texto plano (se escapa)
     * @param datos   etiqueta -> valor, en orden; los valores se escapan y los null se omiten
     */
    static String correo(String titulo, String nombre, String parrafo, Map<String, String> datos, String url,
                         String textoBoton) {
        StringBuilder filas = new StringBuilder();
        datos.forEach((etiqueta, valor) -> {
            if (valor != null && !valor.isBlank()) {
                filas.append("<tr><td style=\"padding:4px 12px 4px 0;color:#6b7280;width:140px;vertical-align:top;\">")
                        .append(esc(etiqueta)).append("</td><td style=\"padding:4px 0;\">")
                        .append(esc(valor)).append("</td></tr>");
            }
        });
        return """
                <!doctype html>
                <html lang="es">
                  <body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:24px 0;">
                      <tr><td align="center">
                        <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:8px;overflow:hidden;">
                          <tr><td style="background:%s;padding:18px 24px;">
                            <span style="color:#ffffff;font-size:16px;font-weight:bold;">%s</span>
                          </td></tr>
                          <tr><td style="padding:24px;">
                            <h1 style="font-size:18px;margin:0 0 16px;">%s</h1>
                            <p style="margin:0 0 12px;">Hola %s,</p>
                            <p style="margin:0 0 16px;">%s</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;font-size:14px;margin:0 0 20px;">%s</table>
                            <a href="%s" style="display:inline-block;background:%s;color:#ffffff;text-decoration:none;font-weight:bold;padding:10px 18px;border-radius:6px;">%s</a>
                          </td></tr>
                          <tr><td style="padding:14px 24px;background:#f9fafb;font-size:12px;color:#6b7280;">
                            Correo automático de %s. No respondas a esta dirección.
                          </td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(AZUL, APP, esc(titulo), esc(nombre), esc(parrafo), filas, esc(url), AZUL,
                esc(textoBoton), APP);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
