package com.tdem.logistica.storage;

import com.tdem.logistica.common.ApiException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

/**
 * Sube archivos a cualquier storage compatible con S3 (DEC-L015/DEC-L016):
 * <ul>
 *   <li>Supabase Storage del proyecto de RRHH (actual): endpoint
 *       {@code https://<ref>.storage.supabase.co/storage/v1/s3}, region del
 *       proyecto, llaves de Storage -> S3 Connection (solo dan acceso a
 *       Storage, nunca a la BD; NO usar la service_role key).</li>
 *   <li>Cloudflare R2 (opcional a futuro): endpoint
 *       {@code https://<account>.r2.cloudflarestorage.com}, region {@code auto}.</li>
 * </ul>
 * Las lecturas no pasan por el backend: el navegador carga la imagen directo
 * desde la URL publica del bucket ({@code app.storage.public-url}).
 */
@Component
public class S3StorageClient implements StorageClient {

    // Cada subida usa un path con UUID nuevo (nunca se sobrescribe un objeto),
    // asi que el navegador/CDN puede cachear el archivo indefinidamente.
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3;
    private final String bucket;
    private final String publicUrlBase;
    private final boolean configurado;

    public S3StorageClient(
            @Value("${app.storage.endpoint:}") String endpoint,
            @Value("${app.storage.region:auto}") String region,
            @Value("${app.storage.access-key-id:}") String accessKeyId,
            @Value("${app.storage.secret-access-key:}") String secretAccessKey,
            @Value("${app.storage.bucket:logistica-productos}") String bucket,
            @Value("${app.storage.public-url:}") String publicUrl) {
        this.configurado = !endpoint.isBlank() && !accessKeyId.isBlank()
                && !secretAccessKey.isBlank() && !publicUrl.isBlank();
        this.bucket = bucket;
        this.publicUrlBase = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        this.s3 = configurado
                ? S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        // Ni R2 ni Supabase soportan todos los checksums que el SDK
                        // agrega por defecto desde la 2.30; solo se envian cuando la
                        // operacion los exige (recomendacion de ambos proveedores).
                        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                        .httpClientBuilder(UrlConnectionHttpClient.builder())
                        .build()
                : null;
    }

    @Override
    public String subir(String path, byte[] contenido, String contentType) {
        if (!configurado) {
            throw ApiException.badRequest(
                    "El almacenamiento de archivos no esta configurado (STORAGE_ENDPOINT / STORAGE_ACCESS_KEY_ID / "
                            + "STORAGE_SECRET_ACCESS_KEY / STORAGE_PUBLIC_URL).");
        }
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(path)
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .cacheControl(CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(contenido));
        return publicUrlBase + path;
    }

    @PreDestroy
    void cerrar() {
        if (s3 != null) {
            s3.close();
        }
    }
}
