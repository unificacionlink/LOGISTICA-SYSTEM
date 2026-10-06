package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "proveedor")
@Getter
@Setter
@NoArgsConstructor
public class Proveedor {

    @Id
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "ruc", nullable = false, unique = true)
    private String ruc;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "email")
    private String email;

    @Column(name = "whatsapp_phone")
    private String whatsappPhone;

    @Column(name = "supplier_type", nullable = false)
    private String supplierType;

    @Column(name = "preferred_delivery_mode")
    private String preferredDeliveryMode;

    @Column(name = "active", nullable = false)
    private boolean active;
}
