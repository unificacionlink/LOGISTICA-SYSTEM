package com.tdem.logistica.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Limpieza nocturna de fotos de producto (DEC-L044): el usuario no quiere
 * conservar fotos reemplazadas.
 * <ol>
 *   <li>Fotos de producto inactivas (reemplazos anteriores a DEC-L044): se
 *       borran su registro y su documento, y el archivo cuando la BD confirma.</li>
 *   <li>Archivos de fotos de producto que ningun documento usa (subidas que
 *       fallaron a medias). Solo carpetas de fotos: {@code <product_id>/...}
 *       (subida desde la app) y {@code sku/...} (scripts/cargar_imagenes.py);
 *       nunca cotizaciones, caja chica ni envios. Solo si tienen mas de 24 h,
 *       para no borrar una subida en curso.</li>
 * </ol>
 */
@Component
public class LimpiezaFotos {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaFotos.class);
    private static final Duration MARGEN = Duration.ofHours(24);

    public record Resultado(int registros, int archivos) {
    }

    private final JdbcTemplate jdbc;
    private final StorageClient storage;

    public LimpiezaFotos(JdbcTemplate jdbc, StorageClient storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @Scheduled(cron = "${app.storage.limpieza-cron:0 30 3 * * *}", zone = "America/Lima")
    public void programada() {
        try {
            Resultado r = limpiar(Instant.now());
            if (r.registros() > 0 || r.archivos() > 0) {
                log.info("Limpieza de fotos: {} registros y {} archivos borrados.", r.registros(), r.archivos());
            }
        } catch (RuntimeException e) {
            log.warn("Limpieza de fotos fallo: {}", e.getMessage());
        }
    }

    /**
     * Sin transaccion propia (la llama el propio bean): el paso 1 es una sola
     * sentencia SQL, atomica; sus archivos se borran recien despues de ejecutarla.
     */
    public Resultado limpiar(Instant ahora) {
        // 1. Fotos reemplazadas que siguen en la BD.
        List<String> urlsReemplazadas = jdbc.queryForList("""
                select d.file_url from producto_imagen pi join documento d on d.document_id = pi.document_id
                 where not pi.active""", String.class);
        int registros = jdbc.update("""
                with borradas as (delete from producto_imagen where not active returning document_id)
                delete from documento d using borradas b
                 where d.document_id = b.document_id
                   and not exists (select 1 from documento_relacion r where r.document_id = d.document_id)""");
        BorradoDiferido.programar(storage, urlsReemplazadas, List.of());

        // 2. Archivos de fotos sin documento que los use.
        Set<String> productos = new HashSet<>(jdbc.queryForList("select product_id::text from producto", String.class));
        Set<String> enUso = new HashSet<>(jdbc.queryForList("select file_url from documento where file_url is not null", String.class));
        enUso.removeAll(urlsReemplazadas);
        Instant limite = ahora.minus(MARGEN);
        int archivos = 0;
        for (StorageClient.Archivo a : storage.listar()) {
            if (!esFotoDeProducto(a.clave(), productos) || enUso.contains(a.url())
                    || a.modificado() == null || a.modificado().isAfter(limite)
                    || urlsReemplazadas.contains(a.url())) {
                continue;
            }
            BorradoDiferido.borrar(storage, a.url());
            archivos++;
        }
        return new Resultado(registros, archivos);
    }

    static boolean esFotoDeProducto(String clave, Set<String> productos) {
        if (clave.startsWith("sku/")) {
            return true;
        }
        int barra = clave.indexOf('/');
        if (barra <= 0) {
            return false;
        }
        String carpeta = clave.substring(0, barra);
        try {
            UUID.fromString(carpeta);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return productos.contains(carpeta);
    }
}
