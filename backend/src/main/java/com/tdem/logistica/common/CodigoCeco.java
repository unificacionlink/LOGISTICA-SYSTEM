package com.tdem.logistica.common;

import java.util.Locale;

/**
 * Forma canonica de un CECO. Codigo (DEC-L032): en RR.HH. el mismo CECO
 * aparece como {@code 0102} y como {@code 102} (Excel le quito el cero); un
 * codigo solo numerico se rellena a 4 digitos, igual que el DNI a 8
 * (DEC-L028). Nombre (DEC-L033): en mayusculas, con tildes, sin espacios
 * repetidos ({@code LIMA LOGÍSTICA}).
 */
public final class CodigoCeco {

    private CodigoCeco() {
    }

    public static String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        String c = codigo.trim().toUpperCase(Locale.ROOT);
        return c.matches("\\d{1,3}") ? "0".repeat(4 - c.length()) + c : c;
    }

    public static String nombre(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** true si el codigo ya venia en su forma canonica (su nombre manda al consolidar). */
    public static boolean esCanonico(String codigo) {
        return codigo != null && codigo.trim().toUpperCase(Locale.ROOT).equals(normalizar(codigo));
    }
}
