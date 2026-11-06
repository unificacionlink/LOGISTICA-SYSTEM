package com.tdem.logistica.repository;

import com.tdem.logistica.entity.ReservaStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, UUID> {

    @Query("select coalesce(sum(r.qtyReserved), 0) from ReservaStock r " +
            "where r.almacen.warehouseId = :warehouseId and r.producto.productId = :productId " +
            "and ((:variantId is null and r.variante is null) or r.variante.variantId = :variantId) " +
            "and r.status = 'RESERVADA'")
    BigDecimal sumarReservadoActivo(
            @Param("warehouseId") UUID warehouseId,
            @Param("productId") UUID productId,
            @Param("variantId") UUID variantId);

    /** Reservado (RESERVADA) por item de un REQ, en una sola consulta: [requestItemId, suma]. */
    @Query("select r.requerimientoItem.requestItemId, sum(r.qtyReserved) from ReservaStock r " +
            "where r.requerimientoItem.requerimiento.requestId = :requestId and r.status = 'RESERVADA' " +
            "group by r.requerimientoItem.requestItemId")
    List<Object[]> sumarReservadoPorRequerimiento(@Param("requestId") UUID requestId);

    @Query("select coalesce(sum(r.qtyReserved), 0) from ReservaStock r " +
            "where r.requerimientoItem.requestItemId = :requestItemId and r.status = 'RESERVADA'")
    BigDecimal sumarReservadoPorItem(@Param("requestItemId") UUID requestItemId);

    /**
     * Cantidad del item cubierta con stock existente (Lote 3), despachada o no.
     * Es lo que Compras descuenta del pendiente de compra: las reservas
     * RECEPCION_OC ya estan contadas como cantidad ordenada (DEC-L017).
     */
    @Query("select coalesce(sum(r.qtyReserved), 0) from ReservaStock r " +
            "where r.requerimientoItem.requestItemId = :requestItemId " +
            "and r.origin = 'VERIFICACION_STOCK' and r.status in ('RESERVADA','DESPACHADA')")
    BigDecimal sumarCubiertoPorStockPorItem(@Param("requestItemId") UUID requestItemId);

    /** Lo ya repartido a cada necesidad desde recepciones de OC (DEC-L039). */
    @Query("select r.necesidad.needId, sum(r.qtyReserved) from ReservaStock r " +
            "where r.origin = 'RECEPCION_OC' and r.necesidad.needId in :needIds group by r.necesidad.needId")
    List<Object[]> sumarRecibidoPorNecesidad(@Param("needIds") java.util.Collection<UUID> needIds);

    /** Reservas activas del item en orden de llegada (FIFO), bloqueadas para despachar. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaStock r where r.requerimientoItem.requestItemId = :requestItemId " +
            "and r.status = 'RESERVADA' order by r.reservedAt, r.reservationId")
    List<ReservaStock> findActivasParaDespacho(@Param("requestItemId") UUID requestItemId);

    /** [variantId, reservado activo] por variante del almacen. */
    @Query("select v.variantId, sum(r.qtyReserved) from ReservaStock r join r.variante v " +
            "where r.almacen.warehouseId = :warehouseId and r.status = 'RESERVADA' group by v.variantId")
    List<Object[]> sumarReservadoPorVariante(@Param("warehouseId") UUID warehouseId);

    @Query("select distinct r.requerimientoItem.requerimiento.requestId from ReservaStock r where r.status = 'RESERVADA'")
    List<UUID> findRequerimientosConReservaActiva();

    /** Tracking: lo apartado del stock existente al aprobar (no lo recibido de una OC). */
    @Query("select r from ReservaStock r where r.requerimientoItem.requerimiento.requestId = :requestId " +
            "and r.origin = 'VERIFICACION_STOCK' order by r.reservedAt")
    List<ReservaStock> findDeStockPorRequerimiento(@Param("requestId") UUID requestId);

    /** Costo unitario de la compra por Caja Chica (referencia del valor de la NI). */
    @Query(value = "select unit_cost from caja_chica_item where petty_cash_item_id = :id", nativeQuery = true)
    BigDecimal costoCajaChica(@Param("id") UUID pettyCashItemId);
}
