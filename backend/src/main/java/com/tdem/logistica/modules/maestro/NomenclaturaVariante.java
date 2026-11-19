package com.tdem.logistica.modules.maestro;

import com.tdem.logistica.common.ApiException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Codigo de atributo de una variante segun su tipo (DEC-L075). La variante es
 * SKU + "-" + codigo: talla tal cual, como en el catalogo sembrado (EP01027-L,
 * EP02010-42); clase con C (EP04002-C0); color con CL, modelo con M y otro con
 * OT, seguidos del valor (EP05001-CLAZUL, HE01041-MXR200, MA04080-OT12).
 *
 * <p>Del valor quedan solo letras y numeros (sin tildes ni espacios). Si el
 * valor empieza con el nombre del tipo ("CLASE 0", "COLOR AZUL") esa palabra se
 * quita, y en clase tambien la C que la abrevia ("C0"), para no duplicar el
 * prefijo. El frontend repite esta regla para mostrar el codigo antes de
 * guardar ({@code components/maestro/atributos.ts}).
 */
public final class NomenclaturaVariante {

    /** attribute_code es varchar(20). */
    static final int LARGO_MAXIMO = 20;

    private static final Map<String, String> PREFIJO = Map.of(
            "TALLA", "",
            "CLASE", "C",
            "COLOR", "CL",
            "MODELO", "M",
            "OTRO", "OT");

    private NomenclaturaVariante() {
    }

    public static String codigo(String tipo, String valor) {
        String prefijo = PREFIJO.get(tipo);
        if (prefijo == null) {
            throw ApiException.badRequest("Tipo de atributo invalido (TALLA, CLASE, COLOR, MODELO u OTRO).");
        }
        String limpio = limpiar(valor);
        if (limpio.startsWith(tipo) && limpio.length() > tipo.length()) {
            limpio = limpio.substring(tipo.length());
        }
        if ("CLASE".equals(tipo) && limpio.length() > 1 && limpio.charAt(0) == 'C' && Character.isDigit(limpio.charAt(1))) {
            limpio = limpio.substring(1);
        }
        if (limpio.isEmpty()) {
            throw ApiException.badRequest("Valor de atributo invalido: \"" + valor + "\" (usa letras y numeros).");
        }
        String codigo = prefijo + limpio;
        if (codigo.length() > LARGO_MAXIMO) {
            throw ApiException.badRequest("El codigo de la variante " + codigo + " supera " + LARGO_MAXIMO
                    + " caracteres: abrevia el valor del atributo.");
        }
        return codigo;
    }

    /** Mayusculas, sin tildes y solo letras y numeros. */
    static String limpiar(String valor) {
        String sinTildes = Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
