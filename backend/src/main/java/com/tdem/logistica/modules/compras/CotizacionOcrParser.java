package com.tdem.logistica.modules.compras;

import com.tdem.logistica.modules.compras.dto.OcrCotizacionDto;
import com.tdem.logistica.modules.compras.dto.OcrItemSugeridoDto;
import com.tdem.logistica.modules.compras.dto.OcrLineaDto;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // En una fila de tabla los importes suelen ir sin simbolo de moneda: cualquier numero con 2 decimales.
    private static final Pattern NUMERO_CON_DECIMALES = Pattern.compile(
            "(?<![\\d.,])(\\d{1,3}(?:[.,]\\d{3})*[.,]\\d{2})(?![\\d])");
    private static final Set<String> PALABRAS_VACIAS = Set.of(
            "DE", "DEL", "LA", "LAS", "EL", "LOS", "PARA", "CON", "SIN", "POR", "UND", "UNID", "UNIDAD", "TIPO", "COLOR");

    /** Lo que se busca de cada item en el documento: su codigo, su nombre, la cantidad pedida y lo aprendido del proveedor. */
    public record ItemBuscado(String codigo, String nombre, BigDecimal qty, List<Equivalencia> equivalencias) {
        public ItemBuscado(String codigo, String nombre, BigDecimal qty) {
            this(codigo, nombre, qty, List.of());
        }
    }

    /** Como llama el proveedor al producto (DEC-L053): su codigo y su descripcion normalizada ({@link #clave}). */
    public record Equivalencia(String codigo, String clave) {
    }

    public static final String ORIGEN_SKU = "SKU";
    public static final String ORIGEN_EQUIVALENCIA = "EQUIVALENCIA";
    public static final String ORIGEN_NOMBRE = "NOMBRE";

    private static final Pattern PALABRA_NO_ITEM = Pattern.compile("(?i)\\b(SUB\\s*-?\\s*TOTAL|TOTAL|I\\.?G\\.?V|IMPUESTO|DESCUENTO)\\b");
    private static final Pattern CODIGO_PROVEEDOR = Pattern.compile("^[A-Z0-9][A-Z0-9./-]{2,19}$");
    private static final Pattern NUMERO_SUELTO = Pattern.compile("(?<![A-Z0-9])\\d+(?:[.,]\\d+)*(?![A-Z0-9])");

    private CotizacionOcrParser() {
    }

    public static OcrCotizacionDto parsear(String texto) {
        return parsear(texto, Map.of());
    }

    /** @param codigosPorItem rfqItemId -> codigo de variante (SKU) tal como se le envio al proveedor en la RFQ. */
    public static OcrCotizacionDto parsear(String texto, Map<UUID, String> codigosPorItem) {
        Map<UUID, ItemBuscado> items = new LinkedHashMap<>();
        codigosPorItem.forEach((id, codigo) -> items.put(id, new ItemBuscado(codigo, null, null)));
        return parsearItems(texto, items);
    }

    /** RUC del documento (para saber de que proveedor es antes de buscar sus equivalencias). */
    public static String ruc(String texto) {
        return buscarPrimero(RUC, texto, Matcher::group);
    }

    /**
     * Como {@link #parsear(String, Map)}, pero si el codigo no aparece (lo normal: cada
     * proveedor usa sus propios codigos) busca primero lo aprendido de ese proveedor
     * (DEC-L053) y luego la linea que mas se parece al nombre del producto; con la
     * cantidad pedida distingue el precio unitario del total de linea.
     */
    public static OcrCotizacionDto parsearItems(String texto, Map<UUID, ItemBuscado> items) {
        Map<UUID, String> codigosPorItem = new LinkedHashMap<>();
        items.forEach((id, i) -> codigosPorItem.put(id, i.codigo()));
        String proveedor = detectarProveedor(texto);
        String ruc = ruc(texto);
        LocalDate fecha = buscarFecha(texto);
        String moneda = detectarMoneda(texto);
        // El total final suele aparecer despues del subtotal/IGV en el documento;
        // se prefiere la ultima coincidencia en vez de la primera.
        BigDecimal total = buscarUltimo(TOTAL, texto, m -> parsearMonto(m.group(1)));
        Integer plazo = buscarPrimero(PLAZO, texto, m -> Integer.valueOf(m.group(1)));
        List<OcrLineaDto> lineas = lineasConPrecio(texto);
        List<OcrItemSugeridoDto> itemsSugeridos = sugerirCostosPorItem(texto, codigosPorItem, lineas);
        Set<UUID> encontrados = new java.util.HashSet<>();
        itemsSugeridos.forEach(i -> encontrados.add(i.rfqItemId()));
        for (Map.Entry<UUID, ItemBuscado> e : items.entrySet()) {
            if (encontrados.contains(e.getKey())) continue;
            ItemBuscado item = e.getValue();
            OcrLineaDto porEquivalencia = lineaPorEquivalencia(lineas, item.equivalencias());
            if (porEquivalencia != null) {
                itemsSugeridos.add(new OcrItemSugeridoDto(e.getKey(), item.codigo(),
                        unitarioDeLinea(porEquivalencia.importes(), item.qty()), porEquivalencia.indice(),
                        ORIGEN_EQUIVALENCIA));
                continue;
            }
            if (item.nombre() == null) continue;
            OcrLineaDto porNombre = lineaPorNombre(lineas, item);
            if (porNombre != null) {
                itemsSugeridos.add(new OcrItemSugeridoDto(e.getKey(), item.codigo(),
                        unitarioDeLinea(porNombre.importes(), item.qty()), porNombre.indice(), ORIGEN_NOMBRE));
            }
        }
        return new OcrCotizacionDto(proveedor, ruc, fecha, moneda, total, plazo, itemsSugeridos, texto, lineas);
    }

    /**
     * Busca el codigo de cada item de la RFQ dentro del texto y, si lo
     * encuentra, propone el menor monto en la ventana de texto siguiente
     * como costo unitario (el importe de linea, si aparece, siempre es
     * mayor o igual al costo unitario para cantidad >= 1). La ventana se
     * corta antes de que empiece el codigo del siguiente item para no
     * "leer" montos que en realidad son de otra fila de la tabla.
     */
    private static List<OcrItemSugeridoDto> sugerirCostosPorItem(String texto, Map<UUID, String> codigosPorItem,
                                                                 List<OcrLineaDto> lineas) {
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
                int indice = (int) texto.substring(0, pos).chars().filter(ch -> ch == '\n').count();
                Integer conPrecio = lineas.stream().map(OcrLineaDto::indice).filter(i -> i == indice).findFirst()
                        .orElse(null);
                sugerencias.add(new OcrItemSugeridoDto(entrada.getKey(), codigo, sugerido, conPrecio, ORIGEN_SKU));
            }
        }
        return sugerencias;
    }

    /**
     * Lineas del documento que pueden ser un producto cotizado: tienen texto y
     * importes (o sus importes quedaron en la linea siguiente, cuando el OCR parte
     * la tabla). Se saltan totales, IGV y descuentos.
     */
    public static List<OcrLineaDto> lineasConPrecio(String texto) {
        List<OcrLineaDto> out = new ArrayList<>();
        if (texto == null) return out;
        String[] lineas = texto.split("\\r?\\n");
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (!tieneTexto(linea) || PALABRA_NO_ITEM.matcher(normalizar(linea)).find()) continue;
            List<BigDecimal> importes = importes(linea);
            if (importes.isEmpty() && i + 1 < lineas.length && !tieneTexto(lineas[i + 1])) {
                importes = importes(lineas[i + 1]);
            }
            if (importes.isEmpty()) continue;
            String descripcion = descripcionDe(linea);
            if (descripcion.isBlank()) continue;
            out.add(new OcrLineaDto(i, linea, descripcion, codigoDe(linea), importes));
        }
        return out;
    }

    /** Clave para recordar una descripcion del proveedor: mayusculas, sin tildes, sin montos ni numeros sueltos. */
    public static String clave(String descripcion) {
        if (descripcion == null) return "";
        String t = NUMERO_SUELTO.matcher(normalizar(descripcion)).replaceAll(" ");
        t = t.replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
        return t.length() > 250 ? t.substring(0, 250) : t;
    }

    /**
     * Precio unitario de una linea: si la cantidad pedida se conoce, el importe que
     * multiplicado por ella da otro importe de la linea (el total); si no, el menor
     * que no sea la propia cantidad.
     */
    public static BigDecimal unitarioDeLinea(List<BigDecimal> importes, BigDecimal qty) {
        if (importes == null || importes.isEmpty()) return null;
        if (qty != null && qty.signum() > 0 && qty.compareTo(BigDecimal.ONE) != 0) {
            for (BigDecimal unitario : importes) {
                BigDecimal esperado = unitario.multiply(qty);
                for (BigDecimal total : importes) {
                    if (total != unitario && esperado.signum() > 0
                            && esperado.subtract(total).abs().compareTo(esperado.multiply(new BigDecimal("0.01"))) <= 0) {
                        return unitario;
                    }
                }
            }
        }
        return importes.stream()
                .filter(m -> qty == null || m.compareTo(qty) != 0)
                .min(BigDecimal::compareTo)
                .orElse(importes.get(0));
    }

    /** Linea que coincide con lo aprendido del proveedor: su codigo exacto o su descripcion (DEC-L053). */
    static OcrLineaDto lineaPorEquivalencia(List<OcrLineaDto> lineas, List<Equivalencia> equivalencias) {
        if (equivalencias == null || equivalencias.isEmpty()) return null;
        for (Equivalencia eq : equivalencias) {
            if (eq.codigo() == null || eq.codigo().isBlank()) continue;
            String codigo = normalizar(eq.codigo().trim());
            for (OcrLineaDto l : lineas) {
                if (codigo.equals(l.codigo()) || (" " + normalizar(l.texto()) + " ").contains(" " + codigo + " ")) {
                    return l;
                }
            }
        }
        for (Equivalencia eq : equivalencias) {
            if (eq.clave() == null || eq.clave().length() < 6) continue;
            for (OcrLineaDto l : lineas) {
                String c = clave(l.descripcion());
                if (c.equals(eq.clave()) || (" " + c + " ").contains(" " + eq.clave() + " ")) {
                    return l;
                }
            }
        }
        return null;
    }

    /**
     * La linea con mas palabras del nombre del producto (al menos la mitad, y
     * dos si el nombre tiene dos o mas).
     */
    static OcrLineaDto lineaPorNombre(List<OcrLineaDto> lineas, ItemBuscado item) {
        List<String> palabras = palabrasClave(item.nombre());
        if (palabras.isEmpty()) return null;
        int minimo = Math.max(palabras.size() >= 2 ? 2 : 1, (palabras.size() + 1) / 2);
        OcrLineaDto mejor = null;
        int mejorPuntaje = 0;
        for (OcrLineaDto l : lineas) {
            String linea = " " + normalizar(l.texto()) + " ";
            int puntaje = 0;
            for (String p : palabras) {
                // Prefijo de la palabra: "GUANTE" encuentra "GUANTES", "CASCO" encuentra "CASCOS".
                if (linea.contains(" " + p.substring(0, Math.min(p.length(), 6)))) puntaje++;
            }
            if (puntaje >= minimo && puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejor = l;
            }
        }
        return mejor;
    }

    /** Compatibilidad: costo unitario por nombre, sin pasar por las lineas. */
    static BigDecimal costoPorNombre(String texto, ItemBuscado item) {
        if (texto == null) return null;
        OcrLineaDto l = lineaPorNombre(lineasConPrecio(texto), item);
        return l != null ? unitarioDeLinea(l.importes(), item.qty()) : null;
    }

    private static boolean tieneTexto(String linea) {
        return linea != null && linea.chars().filter(Character::isLetter).count() >= 3;
    }

    /** Texto de la linea sin importes ni el numero de item del inicio. */
    private static String descripcionDe(String linea) {
        String t = NUMERO_CON_DECIMALES.matcher(linea).replaceAll(" ");
        t = t.replaceAll("(?i)S/\\.?|US\\$|\\$", " ").replaceFirst("^\\s*\\d{1,3}[.)-]?\\s+", "");
        t = t.trim().replaceAll("\\s+", " ");
        return t.length() > 250 ? t.substring(0, 250) : t;
    }

    /** Codigo del proveedor si la linea (tras el numero de item) empieza con una palabra con letras y digitos. */
    private static String codigoDe(String linea) {
        String[] partes = normalizar(linea.trim()).split("\\s+");
        int i = 0;
        if (partes.length > 1 && partes[0].matches("\\d{1,3}[.)-]?")) i = 1;
        if (i >= partes.length) return null;
        String p = partes[i];
        boolean letra = p.chars().anyMatch(Character::isLetter);
        boolean digito = p.chars().anyMatch(Character::isDigit);
        return letra && digito && CODIGO_PROVEEDOR.matcher(p).matches() ? p : null;
    }

    private static List<BigDecimal> importes(String linea) {
        List<BigDecimal> out = new ArrayList<>();
        Matcher m = NUMERO_CON_DECIMALES.matcher(linea);
        while (m.find()) {
            BigDecimal monto = parsearMonto(m.group(1));
            if (monto != null && monto.signum() > 0) out.add(monto);
        }
        return out;
    }

    private static List<String> palabrasClave(String nombre) {
        List<String> out = new ArrayList<>();
        if (nombre == null) return out;
        for (String p : normalizar(nombre).split("[^A-Z0-9]+")) {
            if (p.length() >= 3 && !PALABRAS_VACIAS.contains(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    private static String normalizar(String t) {
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
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
