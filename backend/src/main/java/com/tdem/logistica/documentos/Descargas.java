package com.tdem.logistica.documentos;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuesta HTTP de un archivo descargable (PDF, Excel). */
public final class Descargas {

    public static final MediaType PDF = MediaType.APPLICATION_PDF;
    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Descargas() {
    }

    public static ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, MediaType tipo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
