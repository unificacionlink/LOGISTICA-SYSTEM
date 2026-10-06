package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "requerimiento")
@Getter
@Setter
@NoArgsConstructor
public class Requerimiento {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "request_code", nullable = false)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceco_id", nullable = false)
    private Ceco ceco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id")
    private Usuario requester;

    @Column(name = "requester_type", nullable = false)
    private String requesterType;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "justification", nullable = false)
    private String justification;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // RF-068: la fija Logistica (RequerimientoService.programarEntrega).
    @Column(name = "scheduled_delivery_date", insertable = false)
    private LocalDate scheduledDeliveryDate;

    // Snapshots V37 (Tracking): aun no los escribe ningun flujo, solo se leen.

    @Column(name = "delivery_address_snapshot", insertable = false, updatable = false)
    private String deliveryAddressSnapshot;

    @Column(name = "total_value_net_snapshot", insertable = false, updatable = false)
    private BigDecimal totalValueNetSnapshot;
}
