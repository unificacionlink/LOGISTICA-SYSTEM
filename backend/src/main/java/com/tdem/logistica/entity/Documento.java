package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Repositorio logico de archivos: la BD nunca guarda el binario, solo la URL
 * en storage externo (docs_base 16_Producto_Imagen_V32).
 */
@Entity
@Table(name = "documento")
@Getter
@Setter
@NoArgsConstructor
public class Documento {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "status", nullable = false)
    private String status;
}
