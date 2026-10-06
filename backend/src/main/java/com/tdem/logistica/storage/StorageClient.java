package com.tdem.logistica.storage;

/**
 * Almacenamiento externo de archivos (imagenes de producto, cotizaciones). La
 * BD nunca guarda el binario, solo la URL publica resultante (docs_base
 * 16_Producto_Imagen_V32). Implementacion unica: {@link S3StorageClient}
 * (Supabase Storage o Cloudflare R2, segun la configuracion).
 */
public interface StorageClient {

    /** Sube (o reemplaza) el archivo en {@code path} y devuelve su URL publica. */
    String subir(String path, byte[] contenido, String contentType);
}
