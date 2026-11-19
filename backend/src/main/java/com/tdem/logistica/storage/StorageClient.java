package com.tdem.logistica.storage;

import java.time.Instant;
import java.util.List;

/**
 * Almacenamiento externo de archivos (imagenes de producto, cotizaciones). La
 * BD nunca guarda el binario, solo la URL publica resultante (docs_base
 * 16_Producto_Imagen_V32). Implementacion unica: {@link S3StorageClient}
 * (Supabase Storage o Cloudflare R2, segun la configuracion).
 */
public interface StorageClient {

    /** Archivo del bucket: clave (path), URL publica y fecha de la ultima escritura. */
    record Archivo(String clave, String url, Instant modificado) {
    }

    /** Sube (o reemplaza) el archivo en {@code path} y devuelve su URL publica. */
    String subir(String path, byte[] contenido, String contentType);

    /** Borra el archivo de esa URL publica; ignora las URL que no son de este bucket. */
    void borrar(String url);

    /** Todos los archivos del bucket (vacio si el almacenamiento no esta configurado). */
    List<Archivo> listar();

    /**
     * Sube al bucket PRIVADO (DEC-L071) y devuelve la clave. Esos archivos no
     * tienen URL publica: se leen solo con {@link #leerPrivado} desde el backend.
     */
    String subirPrivado(String clave, byte[] contenido, String contentType);

    /** Contenido de un archivo del bucket privado. */
    byte[] leerPrivado(String clave);
}
