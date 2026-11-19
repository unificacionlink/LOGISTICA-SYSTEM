package com.tdem.logistica.modules.proveedores.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Historial comercial del proveedor leido de Compras y Almacen, sin duplicar datos. Montos por moneda. */
public record ComprasProveedorDto(
        Rfqs rfqs,
        List<TotalMoneda> totales,
        List<Cotizacion> cotizaciones,
        List<Orden> ordenes,
        List<Producto> productos) {

    public record Rfqs(int enviadas, int respondidas, BigDecimal diasRespuestaPromedio) {
    }

    public record TotalMoneda(String moneda, int ordenes, BigDecimal total) {
    }

    public record Cotizacion(UUID quotationId, String rfqCode, String numero, OffsetDateTime recibida,
                             String moneda, BigDecimal total, Integer plazoDias, LocalDate vigenteHasta,
                             String status) {
    }

    public record Orden(UUID orderId, String orderCode, String empresa, LocalDate emitida, String status,
                        String moneda, BigDecimal total, String pago, OffsetDateTime aprobada,
                        OffsetDateTime liberada, OffsetDateTime primeraRecepcion) {
    }

    public record Producto(UUID productId, String skuBase, String nombre, String variante, String moneda,
                           int ordenes, BigDecimal cantidad, BigDecimal ultimoPrecio, BigDecimal precioMinimo,
                           BigDecimal precioMaximo, LocalDate ultimaCompra, List<Precio> precios) {
    }

    public record Precio(LocalDate fecha, BigDecimal precio, String orderCode) {
    }
}
