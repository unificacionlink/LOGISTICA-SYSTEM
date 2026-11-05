package com.tdem.logistica.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Reserva de stock por item de requerimiento, creada al confirmar la verificacion de stock. */
@Entity
@Table(name = "reserva_stock")
@Getter
@Setter
@NoArgsConstructor
public class ReservaStock {

    @Id
    @Column(name = "reservation_id")
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_item_id", nullable = false)
    private RequerimientoItem requerimientoItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Almacen almacen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductoVariante variante;

    @Column(name = "qty_reserved", nullable = false)
    private BigDecimal qtyReserved;

    @Column(name = "status", nullable = false)
    private String status;

    /**
     * VERIFICACION_STOCK: stock existente apartado al verificar el REQ.
     * RECEPCION_OC: lo comprado para el REQ, apartado al recibir la OC
     * (DEC-L017). Compras solo descuenta las primeras del pendiente de compra.
     */
    @Column(name = "origin", nullable = false)
    private String origin = ORIGEN_VERIFICACION_STOCK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_item_id")
    private RecepcionItem recepcionItem;

    public static final String ORIGEN_VERIFICACION_STOCK = "VERIFICACION_STOCK";
    public static final String ORIGEN_RECEPCION_OC = "RECEPCION_OC";
    public static final String ORIGEN_CAJA_CHICA = "CAJA_CHICA";

    /** Item de la compra por Caja Chica que origino la reserva (DEC-L026). */
    @Column(name = "petty_cash_item_id")
    private UUID pettyCashItemId;
    /** Necesidad atendida con lo recibido de la OC (reparto del consolidado, DEC-L039). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "need_id")
    private NecesidadCompra necesidad;

    @Column(name = "reserved_at", insertable = false, updatable = false)
    private OffsetDateTime reservedAt;
}
