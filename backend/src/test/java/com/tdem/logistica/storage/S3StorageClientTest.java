package com.tdem.logistica.storage;

import com.tdem.logistica.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sin contexto de Spring ni red: verifica que el cliente S3 se construya con
 * la configuracion de cada proveedor, y que sin credenciales la subida falle
 * con un mensaje de validacion en vez de un error del SDK.
 */
class S3StorageClientTest {

    @Test
    void sinCredencialesLaSubidaFallaConErrorDeValidacion() {
        S3StorageClient client = new S3StorageClient("", "auto", "", "", "logistica-productos", "");
        assertThrows(ApiException.class, () -> client.subir("x/y.png", new byte[]{1}, "image/png"));
    }

    @Test
    void construyeElClienteParaSupabaseStorage() {
        assertDoesNotThrow(() -> new S3StorageClient(
                "https://abcdefghij.storage.supabase.co/storage/v1/s3", "us-east-2", "key", "secret",
                "logistica-productos",
                "https://abcdefghij.supabase.co/storage/v1/object/public/logistica-productos").cerrar());
    }

    @Test
    void construyeElClienteParaCloudflareR2() {
        assertDoesNotThrow(() -> new S3StorageClient(
                "https://0123456789abcdef.r2.cloudflarestorage.com", "auto", "key", "secret",
                "productos", "https://img.ejemplo.com").cerrar());
    }
}
