package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orden de compra. OC_NORMAL sale de una cotizacion; OC_DIRECTA (DEC-L080) no
 * tiene cotizacion y puede llevar lineas de servicio (orden_item sin producto).
 */
@Entity
@Table(name = "orden_abastecimiento")
@Getter
@Setter
@NoArgsConstructor
public class OrdenAbastecimiento {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_code", nullable = false)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Cotizacion cotizacion;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "payment_condition", nullable = false)
    private String paymentCondition;

    @Column(name = "delivery_mode", nullable = false)
    private String deliveryMode;

    @Column(name = "delivery_location")
    private String deliveryLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_code", nullable = false)
    private Moneda moneda;

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    @Column(name = "status", nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ---- Lote 17 (DEC-L061): aprobacion, pago y liberacion

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    /** PENDIENTE, ANTICIPO o PAGADO: control, no frena la recepcion. */
    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_proof_document_id")
    private Documento constanciaPago;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    /** Lista para recibir en el almacen. */
    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Column(name = "release_note")
    private String releaseNote;

    // ---- Formato F-LOG-09 (DEC-L079)

    /** MAS_IGV, IGV_INCLUIDO o NO_AFECTO: ver {@code ImpuestoOrden}. */
    @Column(name = "tax_mode", nullable = false)
    private String taxMode = "MAS_IGV";

    @Column(name = "delivery_term")
    private String deliveryTerm;

    @Column(name = "additional_specs")
    private String additionalSpecs;

    // ---- OC directa (DEC-L080): referencias opcionales

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ceco_id")
    private Ceco ceco;

    /** Requerimiento de referencia; solo informativo (no reserva ni atiende). */
    @Column(name = "request_id")
    private UUID requestId;

    /** Solo lineas de servicio: no pasa por el almacen y se cierra al pagarse. */
    @Column(name = "services_only", nullable = false)
    private boolean servicesOnly;

    /** El proveedor entrega en los CECO de destino; no se recibe en el almacen (DEC-L084). */
    @Column(name = "direct_delivery", nullable = false)
    private boolean directDelivery;
}
