package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Vinculo polimorfico archivo-entidad (RF-077/080: guia, ticket, conformidad de un ENVIO). */
@Entity
@Table(name = "documento_relacion")
@Getter
@Setter
@NoArgsConstructor
public class DocumentoRelacion {

    @Id
    @Column(name = "document_relation_id")
    private UUID documentRelationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Documento documento;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
}
