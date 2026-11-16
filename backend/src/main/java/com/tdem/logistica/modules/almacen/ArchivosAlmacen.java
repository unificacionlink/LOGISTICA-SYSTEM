package com.tdem.logistica.modules.almacen;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.Documento;
import com.tdem.logistica.repository.DocumentoRepository;
import com.tdem.logistica.storage.BorradoDiferido;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sube un PDF o imagen del almacen al Storage y lo registra en documento. */
@Component
class ArchivosAlmacen {

    // Sin image/svg+xml, mismo criterio que las cotizaciones y fotos de producto.
    private static final Map<String, String> EXTENSIONES_PERMITIDAS = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final StorageClient storageClient;
    private final DocumentoRepository documentoRepository;

    ArchivosAlmacen(StorageClient storageClient, DocumentoRepository documentoRepository) {
        this.storageClient = storageClient;
        this.documentoRepository = documentoRepository;
    }

    /**
     * @param carpeta prefijo en el bucket (p. ej. {@code envios/<id>}). La extension sale del
     *                content-type validado, no del nombre del cliente: sin path traversal.
     */
    Documento subir(String carpeta, String documentType, MultipartFile file, UUID userId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Selecciona un archivo.");
        }
        String contentType = file.getContentType();
        String extension = contentType != null ? EXTENSIONES_PERMITIDAS.get(contentType) : null;
        if (extension == null) {
            throw ApiException.badRequest("Formato no soportado (usa PDF, JPG, PNG o WEBP).");
        }
        String path = carpeta + "/" + UUID.randomUUID() + extension;
        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido.", e);
        }
        String url = storageClient.subir(path, contenido, contentType);
        // Si la transaccion se revierte, el archivo recien subido no queda huerfano.
        BorradoDiferido.programar(storageClient, List.of(), List.of(url));

        Documento documento = new Documento();
        documento.setDocumentId(UUID.randomUUID());
        documento.setDocumentType(documentType);
        documento.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : path);
        documento.setFileUrl(url);
        documento.setMimeType(contentType);
        documento.setUploadedBy(userId);
        documento.setUploadedAt(Instant.now());
        documento.setStatus("ACTIVO");
        return documentoRepository.save(documento);
    }
}
