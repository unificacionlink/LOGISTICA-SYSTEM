package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.modules.cajachica.dto.LecturaComprobanteDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee lo que pueda de una factura, boleta o ticket peruano (DEC-L095): tipo,
 * serie-numero, RUC y razon social del emisor, fecha de emision, total y las
 * lineas cuyo calculo cuadra (cantidad x precio = importe). Lo que no reconoce
 * con seguridad queda en null para que el usuario lo complete: es preferible
 * un campo vacio a uno mal llenado.
 */
public final class ComprobanteOcrParser {

    private static final Pattern RUC = Pattern.compile("(?<!\\d)(10|15|17|20)\\d{9}(?!\\d)");
    private static final Pattern SERIE_NUMERO = Pattern.compile(
            "(?<![A-Z0-9])([FB][A-Z0-9]{3}|E001|T\\d{3})\\s*[-–—]\\s*(\\d{1,10})(?!\\d)");
    private static final Pattern FECHA = Pattern.compile("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern FECHA_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern SUFIJO_EMPRESA = Pattern.compile(
            "\\b(S\\.?\\s?A\\.?\\s?C\\.?|S\\.?\\s?R\\.?\\s?L\\.?|E\\.?\\s?I\\.?\\s?R\\.?\\s?L\\.?|S\\.?\\s?A\\.?\\s?A\\.?|S\\.\\s?A\\.)(?![A-Z])");
    private static final Pattern MONTO = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})(?!\\d)");
    private static final Pattern TOTAL_EXPLICITO = Pattern.compile(
            "(IMPORTE\\s+TOTAL|TOTAL\\s+A\\s+PAGAR|TOTAL\\s+VENTA|TOTAL\\s+S/)[^0-9\\n]{0,15}(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})");
    private static final Pattern TOTAL = Pattern.compile("(?<!SUB)(?<!SUB )(?<!SUB-)TOTAL[^0-9\\n]{0,20}(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})");
    private static final Pattern NO_TOTAL = Pattern.compile("IGV|GRAVAD|EXONERAD|INAFECT|DESCUENTO|GRATUIT|ISC|ANTICIPO");
    private static final String NUM = "(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2,4})";
    private static final String CANT = "(\\d{1,5}(?:[.,]0{1,3})?)";
    private static final String UNIDAD = "(?:(?:UND|UNID|UNIDAD|NIU|ZZ|UN|U|KG|KGM|CJ|CAJA|PAR|PQT|PAQ|GLN|LT|LTR|MT|MTR|M|BOL|ROLLO|RLL|PZA|DOC|DOCENA|JGO|BLS|SET)\\.?\\s+)?";
    // "2 UND CINTA DE EMBALAJE 3.50 7.00" y "CINTA DE EMBALAJE 2 UND 3.50 7.00".
    private static final Pattern LINEA_CANT_PRIMERO = Pattern.compile(
            "^\\s*(?:\\d{1,3}[.)]?\\s+)??" + CANT + "\\s+" + UNIDAD + "(.*?[A-Z].*?)\\s+(?:S/\\s*)?" + NUM + "\\s+(?:S/\\s*)?" + NUM + "\\s*$");
    private static final Pattern LINEA_DESC_PRIMERO = Pattern.compile(
            "^\\s*(.*?[A-Z].*?)\\s+" + CANT + "\\s+" + UNIDAD + "(?:S/\\s*)?" + NUM + "\\s+(?:S/\\s*)?" + NUM + "\\s*$");
    private static final Set<String> NO_ES_LINEA = Set.of("TOTAL", "IGV", "GRAVAD", "SUBTOTAL", "SUB TOTAL", "DESCUENTO",
            "RUC", "FECHA", "VUELTO", "EFECTIVO", "REDONDEO");
    private static final BigDecimal IGV = new BigDecimal("1.18");
    private static final int MAX_LINEAS = 50;

    private ComprobanteOcrParser() {
    }

    /**
     * @param rucPropios   RUC de las empresas del grupo: aparecen como cliente, no como emisor.
     * @param nombresPropios razones sociales del grupo, por la misma razon.
     * @param hoy          la fecha leida no puede ser futura.
     */
    public static LecturaComprobanteDto leer(String texto, Collection<String> rucPropios,
                                             Collection<String> nombresPropios, LocalDate hoy) {
        if (texto == null || texto.isBlank()) {
            return LecturaComprobanteDto.vacia();
        }
        String t = normalizar(texto);
        String tipo = tipo(t);
        String[] serieNumero = serieNumero(t);
        if (tipo == null && serieNumero != null) {
            tipo = serieNumero[0].startsWith("F") ? "FACTURA" : serieNumero[0].startsWith("B") ? "BOLETA" : null;
        }
        BigDecimal total = total(t);
        List<LecturaComprobanteDto.Linea> lineas = lineas(t, total);
        return new LecturaComprobanteDto(tipo,
                serieNumero != null ? serieNumero[0] : null,
                serieNumero != null ? serieNumero[1] : null,
                ruc(t, rucPropios),
                proveedor(texto, rucPropios, nombresPropios),
                fecha(t, hoy),
                total,
                lineas);
    }

    static String tipo(String t) {
        if (t.contains("FACTURA ELECTR")) return "FACTURA";
        if (t.contains("BOLETA")) return "BOLETA";
        if (t.contains("FACTURA")) return "FACTURA";
        return null;  // DEC-L096: la caja chica solo registra boletas y facturas
    }

    static String[] serieNumero(String t) {
        Matcher m = SERIE_NUMERO.matcher(t);
        return m.find() ? new String[] {m.group(1), m.group(2)} : null;
    }

    /** El primer RUC que no es de una empresa del grupo: el del emisor va en el membrete. */
    static String ruc(String t, Collection<String> rucPropios) {
        Matcher m = RUC.matcher(t);
        while (m.find()) {
            if (!rucPropios.contains(m.group())) return m.group();
        }
        return null;
    }

    /** Primera linea con sufijo societario que no es del cliente (el grupo) ni la etiqueta del cliente. */
    static String proveedor(String texto, Collection<String> rucPropios, Collection<String> nombresPropios) {
        List<String> propios = nombresPropios.stream().map(ComprobanteOcrParser::nucleo).filter(s -> !s.isEmpty()).toList();
        for (String original : texto.split("\\r?\\n")) {
            String linea = normalizar(original).trim();
            if (linea.length() < 5 || !SUFIJO_EMPRESA.matcher(linea).find()) continue;
            if (linea.matches(".*\\b(SENOR|SENORES|CLIENTE|ADQUIRIENTE|USUARIO)\\b.*")) continue;
            if (rucPropios.stream().anyMatch(linea::contains)) continue;
            String compacta = linea.replaceAll("[^A-Z0-9]", "");
            if (propios.stream().anyMatch(compacta::contains)) continue;
            String limpia = original.trim()
                    .replaceFirst("(?i)^(raz[oó]n\\s+social|emisor|proveedor)\\s*:?\\s*", "")
                    .replaceFirst("(?i)\\s*-?\\s*R\\.?U\\.?C\\.?.*$", "")
                    .replaceAll("\\s+", " ").trim();
            if (limpia.chars().filter(Character::isLetter).count() < 4) continue;
            return limpia.length() > 200 ? limpia.substring(0, 200) : limpia;
        }
        return null;
    }

    /** La fecha junto a "emision"; si no la hay, la primera fecha valida. Nunca una futura. */
    static LocalDate fecha(String t, LocalDate hoy) {
        int emision = t.indexOf("EMISION");
        if (emision >= 0) {
            LocalDate f = primeraFecha(t.substring(emision, Math.min(t.length(), emision + 60)), hoy);
            if (f != null) return f;
        }
        return primeraFecha(t, hoy);
    }

    private static LocalDate primeraFecha(String t, LocalDate hoy) {
        Matcher m = FECHA.matcher(t);
        while (m.find()) {
            LocalDate f = construir(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)), hoy);
            if (f != null) return f;
        }
        Matcher iso = FECHA_ISO.matcher(t);
        while (iso.find()) {
            LocalDate f = construir(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)), hoy);
            if (f != null) return f;
        }
        return null;
    }

    private static LocalDate construir(int anio, int mes, int dia, LocalDate hoy) {
        if (anio < 100) anio += 2000;
        try {
            LocalDate f = LocalDate.of(anio, mes, dia);
            return f.isAfter(hoy) || f.isBefore(hoy.minusYears(2)) ? null : f;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "Importe total"/"Total a pagar" si aparece; si no, el ultimo "total" que no sea IGV, gravada, etc. */
    static BigDecimal total(String t) {
        BigDecimal explicito = null;
        Matcher e = TOTAL_EXPLICITO.matcher(t);
        while (e.find()) {
            BigDecimal v = monto(e.group(2));
            if (v != null && v.signum() > 0) explicito = v;
        }
        if (explicito != null) return explicito;
        BigDecimal ultimo = null;
        for (String linea : t.split("\\r?\\n")) {
            if (NO_TOTAL.matcher(linea).find()) continue;
            Matcher m = TOTAL.matcher(linea);
            while (m.find()) {
                BigDecimal v = monto(m.group(1));
                if (v != null && v.signum() > 0) ultimo = v;
            }
        }
        return ultimo;
    }

    /**
     * Solo las lineas cuyo calculo cuadra (cantidad entera x precio = importe): asi
     * una linea mal leida no se cuela. Si los precios vienen sin IGV y con IGV
     * suman el total, se devuelven con IGV (en la caja chica se registra lo pagado).
     */
    static List<LecturaComprobanteDto.Linea> lineas(String t, BigDecimal total) {
        List<LecturaComprobanteDto.Linea> out = new ArrayList<>();
        for (String linea : t.split("\\r?\\n")) {
            if (out.size() >= MAX_LINEAS) break;
            String l = linea.trim().replaceAll("\\s+", " ");
            if (l.length() < 6 || NO_ES_LINEA.stream().anyMatch(l::contains)) continue;
            LecturaComprobanteDto.Linea leida = linea(LINEA_CANT_PRIMERO.matcher(l), 1, 2, 3, 4);
            if (leida == null) {
                leida = linea(LINEA_DESC_PRIMERO.matcher(l), 2, 1, 3, 4);
            }
            if (leida != null) out.add(leida);
        }
        if (out.isEmpty() || total == null) return out;
        BigDecimal suma = suma(out);
        if (suma.subtract(total).abs().compareTo(new BigDecimal("0.05")) <= 0) return out;
        List<LecturaComprobanteDto.Linea> conIgv = out.stream().map(x -> new LecturaComprobanteDto.Linea(x.descripcion(),
                x.cantidad(), x.precioUnitario().multiply(IGV).setScale(2, RoundingMode.HALF_UP))).toList();
        return suma(conIgv).subtract(total).abs().compareTo(new BigDecimal("0.05")) <= 0 ? conIgv : out;
    }

    private static LecturaComprobanteDto.Linea linea(Matcher m, int grupoCant, int grupoDesc, int grupoUnit, int grupoImporte) {
        if (!m.matches()) return null;
        BigDecimal cantidad = monto(m.group(grupoCant).replace(",", "."));
        BigDecimal unitario = monto(m.group(grupoUnit));
        BigDecimal importe = monto(m.group(grupoImporte));
        if (cantidad == null || unitario == null || importe == null || cantidad.signum() <= 0 || unitario.signum() <= 0) {
            return null;
        }
        if (cantidad.stripTrailingZeros().scale() > 0) return null;  // las unidades son contables
        BigDecimal calculado = cantidad.multiply(unitario);
        BigDecimal tolerancia = importe.multiply(new BigDecimal("0.01")).max(new BigDecimal("0.02"));
        if (calculado.subtract(importe).abs().compareTo(tolerancia) > 0) return null;
        String descripcion = m.group(grupoDesc).replaceFirst("^[\\d.)\\s-]+(?=[A-Z])", "").trim();
        if (descripcion.chars().filter(Character::isLetter).count() < 3) return null;
        if (descripcion.length() > 300) descripcion = descripcion.substring(0, 300);
        return new LecturaComprobanteDto.Linea(descripcion, cantidad.setScale(0, RoundingMode.HALF_UP),
                unitario.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal suma(List<LecturaComprobanteDto.Linea> lineas) {
        return lineas.stream().map(x -> x.cantidad().multiply(x.precioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** "1,234.50", "1.234,50", "12,50" o "12.50". */
    static BigDecimal monto(String s) {
        if (s == null) return null;
        String v = s.trim();
        int coma = v.lastIndexOf(',');
        int punto = v.lastIndexOf('.');
        if (coma >= 0 && punto >= 0) {
            v = coma > punto ? v.replace(".", "").replace(',', '.') : v.replace(",", "");
        } else if (coma >= 0) {
            v = v.length() - coma - 1 <= 2 ? v.replace(',', '.') : v.replace(",", "");
        } else if (punto >= 0 && v.length() - punto - 1 == 3 && v.indexOf('.') != punto) {
            v = v.replace(".", "");
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nucleo(String nombre) {
        String n = normalizar(nombre).replaceAll("[^A-Z0-9 ]", " ").trim();
        String[] palabras = n.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, palabras.length); i++) sb.append(palabras[i]);
        return sb.toString();
    }

    static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
    }
}
