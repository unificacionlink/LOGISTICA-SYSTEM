package com.tdem.logistica.modules.compras;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Codigo legible unico por empresa: <prefijo>-<empresa>-<yyyyMMdd>-<sufijo>. */
public final class CodigoCompra {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private CodigoCompra() {
    }

    public static String generar(String prefijo, String empresaCode) {
        String fecha = LocalDate.now(LIMA).format(YYYYMMDD);
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefijo + "-" + empresaCode + "-" + fecha + "-" + sufijo;
    }
}
