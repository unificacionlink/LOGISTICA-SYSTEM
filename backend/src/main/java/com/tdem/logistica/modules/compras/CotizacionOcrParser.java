package com.tdem.logistica.modules.compras;

import com.tdem.logistica.modules.compras.dto.OcrCotizacionDto;
import com.tdem.logistica.modules.compras.dto.OcrItemSugeridoDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristicas por regex sobre el texto extraido del documento (texto real
 * del PDF, o via OCR si es un escaneo — sin LLM, sin costo): busca RUC/
 * proveedor/fecha/moneda/total/plazo por patrones y palabras clave comunes
 * en cotizaciones peruanas, y ademas intenta un costo por item buscando en
 * el texto los codigos de producto que YA se enviaron en la RFQ (mas
 * confiable entre proveedores distintos que tratar de "entender" una tabla
 * con formato libre). Nunca 100% confiable — son solo sugerencias que el
 * usuario valida (ver docs_base: "el sistema propone y el usuario
 * valida/corrige antes de guardar").
 */
public final class CotizacionOcrParser {

    private static final Pattern RUC = Pattern.compile("\\b(10|15|17|20)\\d{9}\\b");
    private static final Pattern SUFIJO_EMPRESA = Pattern.compile(
            "(?i)\\b(S\\.?\\s?A\\.?\\s?C\\.?|S\\.?\\s?R\\.?\\s?L\\.?|E\\.?\\s?I\\.?\\s?R\\.?\\s?L\\.?|S\\.?\\s?A\\.?)\\b");
    // "19-Ago-2026" / "19 de agosto de 2026" / "19 Ago 2026" — comun en cotizaciones peruanas.
    private static final Pattern FECHA_NUMERICA = Pattern.compile("\\b(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})\\b");
    private static final Pattern FECHA_MES_TEXTO = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*(?:de\\s*)?[/\\-\\s]\\s*([a-zA-Zé]{3,9})\\.?\\s*(?:de\\s*)?[/\\-\\s]\\s*(\\d{2,4})\\b");
    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("abr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("ago", 8),
            Map.entry("set", 9), Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dic", 12));
    // Excluye "Sub-Total"/"Sub Total": el importe que interesa es el total final, no el parcial.
    private static final Pattern TOTAL = Pattern.compile(
            "(?i)(?<!sub.{0,3})total[^0-9]{0,20}([0-9]{1,3}(?:[.,][0-9]{3})*[.,][0-9]{2})");
    private static final Pattern PLAZO = Pattern.compile("(?i)(\\d{1,3})\\s*d[ií]as?");
    // Un monto de por item puede venir sin centavos (ej. "S/. 559"), a diferencia del total.
    private static final Pattern MONTO = Pattern.compile("(?:S/\\.?|US\\$|\\$)\\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?)");
    private static final int VENTANA_BUSQUEDA_ITEM = 120;

    private CotizacionOcrParser() {
    }

    public static OcrCotizacionDto parsear(String texto) {
        return parsear(texto, Map.of());
    }

    /** @param codigosPorItem rfqItemId -> codigo de variante (SKU) tal como se le envio al proveedor en la RFQ. */
    public static OcrCotizacionDto parsear(String texto, Map<UUID, String> codigosPorItem) {
        String proveedor = detectarProveedor(texto);
        String ruc = buscarPrimero(RUC, texto, Matcher::group);
        LocalDate fecha = buscarFecha(texto);
        String moneda = detectarMoneda(texto);
        // El total final suele aparecer despues del subtotal/IGV en el documento;
        // se prefiere la ultima coincidencia en vez de la primera.
        BigDecimal total = buscarUltimo(TOTAL, texto, m -> parsearMonto(m.group(1)));
        Integer plazo = buscarPrimero(PLAZO, texto, m -> Integer.valueOf(m.group(1)));
        List<OcrItemSugeridoDto> itemsSugeridos = sugerirCostosPorItem(texto, codigosPorItem);
        return new OcrCotizacionDto(proveedor, ruc, fecha, moneda, total, plazo, itemsSugeridos, texto);
    }

    /**
     * Busca el codigo de cada item de la RFQ dentro del texto y, si lo
     * encuentra, propone el menor monto en la ventana de texto siguiente
     * como costo unitario (el importe de linea, si aparece, siempre es
     * mayor o igual al costo unitario para cantidad >= 1). La ventana se
     * corta antes de que empiece el codigo del siguiente item para no
     * "leer" montos que en realidad son de otra fila de la tabla.
     */
    private static List<OcrItemSugeridoDto> sugerirCostosPorItem(String texto, Map<UUID, String> codigosPorItem) {
        List<OcrItemSugeridoDto> sugerencias = new ArrayList<>();
        if (texto == null || codigosPorItem == null || codigosPorItem.isEmpty()) return sugerencias;
        String textoMayus = texto.toUpperCase();

        Map<UUID, Integer> posicionPorItem = new HashMap<>();
        for (Map.Entry<UUID, String> entrada : codigosPorItem.entrySet()) {
            if (entrada.getValue() == null || entrada.getValue().isBlank()) continue;
            int pos = textoMayus.indexOf(entrada.getValue().toUpperCase());
            if (pos >= 0) posicionPorItem.put(entrada.getKey(), pos);
        }

        for (Map.Entry<UUID, Integer> entrada : posicionPorItem.entrySet()) {
            int pos = entrada.getValue();
            String codigo = codigosPorItem.get(entrada.getKey());
            int finVentana = Math.min(texto.length(), pos + codigo.length() + VENTANA_BUSQUEDA_ITEM);
            for (int otraPos : posicionPorItem.values()) {
                if (otraPos > pos && otraPos < finVentana) finVentana = otraPos;
            }
            BigDecimal sugerido = buscarMenorMonto(texto.substring(pos, finVentana));
            if (sugerido != null) {
                sugerencias.add(new OcrItemSugeridoDto(entrada.getKey(), codigo, sugerido));
            }
        }
        return sugerencias;
    }

    private static BigDecimal buscarMenorMonto(String ventana) {
        BigDecimal menor = null;
        Matcher m = MONTO.matcher(ventana);
        while (m.find()) {
            BigDecimal monto = parsearMonto(m.group(1));
            if (monto != null && (menor == null || monto.compareTo(menor) < 0)) {
                menor = monto;
            }
        }
        return menor;
    }

    /** Primera linea que trae un sufijo societario peruano (SAC/SRL/EIRL/SA) — suele ser el membrete del proveedor. */
    private static String detectarProveedor(String texto) {
        if (texto == null) return null;
        for (String linea : texto.split("\\r?\\n")) {
            String limpia = linea.trim();
            if (limpia.length() >= 4 && limpia.length() <= 120 && SUFIJO_EMPRESA.matcher(limpia).find()) {
                return limpia;
            }
        }
        return null;
    }

    private static String detectarMoneda(String texto) {
        if (texto == null) return null;
        if (texto.contains("S/") || texto.toUpperCase().contains("PEN") || texto.toUpperCase().contains("SOLES")) {
            return "PEN";
        }
        if (texto.contains("$") || texto.toUpperCase().contains("USD") || texto.toUpperCase().contains("US$")) {
            return "USD";
        }
        return null;
    }

    private static LocalDate buscarFecha(String texto) {
        if (texto == null) return null;
        LocalDate fecha = buscarPrimero(FECHA_NUMERICA, texto, m -> construirFecha(
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        if (fecha != null) return fecha;
        return buscarPrimero(FECHA_MES_TEXTO, texto, m -> {
            Integer mes = MESES.get(m.group(2).toLowerCase().substring(0, 3));
            if (mes == null) return null;
            return construirFecha(Integer.parseInt(m.group(1)), mes, Integer.parseInt(m.group(3)));
        });
    }

    private static LocalDate construirFecha(int dia, int mes, int anio) {
        if (anio < 100) anio += 2000;
        if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
        try {
            return LocalDate.of(anio, mes, dia);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parsearMonto(String texto) {
        try {
            String limpio = texto.trim();
            boolean tieneComa = limpio.contains(",");
            boolean tienePunto = limpio.contains(".");
            if (tieneComa && tienePunto) {
                // El separador que aparece al final del numero es el decimal; el otro es de miles.
                limpio = limpio.lastIndexOf(',') > limpio.lastIndexOf('.')
                        ? limpio.replace(".", "").replace(",", ".")
                        : limpio.replace(",", "");
            } else if (tieneComa) {
                // Solo coma: decimal unicamente si quedan 1-2 digitos despues (ej. "12,50");
                // con 3 digitos es separador de miles sin centavos (ej. "1,420" = 1420).
                int digitosDespues = limpio.length() - limpio.lastIndexOf(',') - 1;
                limpio = digitosDespues <= 2 ? limpio.replace(",", ".") : limpio.replace(",", "");
            }
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private interface Extractor<T> {
        T extraer(Matcher m) throws Exception;
    }

    /** Primera coincidencia valida (la primera cuya extraccion no falla ni devuelve null). */
    private static <T> T buscarPrimero(Pattern patron, String texto, Extractor<T> extractor) {
        if (texto == null) return null;
        Matcher m = patron.matcher(texto);
        while (m.find()) {
            T valor = extraerSeguro(m, extractor);
            if (valor != null) return valor;
        }
        return null;
    }

    /** Ultima coincidencia valida en el texto (para "total" cuando hay varios importes). */
    private static <T> T buscarUltimo(Pattern patron, String texto, Extractor<T> extractor) {
        if (texto == null) return null;
        Matcher m = patron.matcher(texto);
        T ultimo = null;
        while (m.find()) {
            T valor = extraerSeguro(m, extractor);
            if (valor != null) ultimo = valor;
        }
        return ultimo;
    }

    private static <T> T extraerSeguro(Matcher m, Extractor<T> extractor) {
        try {
            return extractor.extraer(m);
        } catch (Exception e) {
            return null;
        }
    }
}
