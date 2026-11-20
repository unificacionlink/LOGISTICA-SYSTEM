package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "active", nullable = false)
    private boolean active;

    // ---- Cabecera del formato F-LOG-09 (DEC-L079)

    @Column(name = "ruc")
    private String ruc;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    /** Claves del bucket privado (DEC-L071); null si no se subio la imagen. */
    @Column(name = "logo_key")
    private String logoKey;

    /** Firma escaneada del V°B° Gerencia (Gerente General). */
    @Column(name = "signature_manager_key")
    private String firmaGerenciaKey;

    /** Firma escaneada del V°B° Logistica (Gestor de Compras). */
    @Column(name = "signature_buyer_key")
    private String firmaLogisticaKey;
}
