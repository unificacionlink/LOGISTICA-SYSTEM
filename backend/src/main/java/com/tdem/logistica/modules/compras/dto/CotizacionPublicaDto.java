package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que ve el proveedor al abrir su enlace (DEC-L052): solo la solicitud y sus
 * lineas. Nada de requerimientos, CECOs, costos ni otros proveedores.
 */
public record CotizacionPublicaDto(
        String empresa,
        String rfqCode,
        String proveedor,
        String proveedorRuc,
        LocalDate fechaEmision,
        String monedaSolicitada,
        Integer plazoSolicitadoDias,
        String condicionPago,
        String modalidadEntrega,
        String lugarEntrega,
        Instant venceEl,
        List<Linea> lineas,
        List<MonedaDto> monedas,
        Enviada enviada) {

    /**
     * Una linea a cotizar. {@code unidadNombre} es null si la unidad del
     * producto aun no esta definida (ND); {@code imagenUrl}, su foto principal.
     */
    public record Linea(
            UUID rfqItemId,
            int numero,
            String sku,
            String producto,
            String atributo,
            String unidad,
            String unidadNombre,
            BigDecimal cantidad,
            String especificacion,
            String imagenUrl) {
    }

    /** Su ultimo envio, para que pueda corregirlo mientras el enlace siga vigente. */
    public record Enviada(
            Instant enviadaEl,
            String currencyCode,
            String paymentCondition,
            String deliveryMode,
            Integer leadDays,
            LocalDate expirationDate,
            String supplierQuoteNumber,
            String documentoNombre,
            List<Precio> precios) {
    }

    public record Precio(UUID rfqItemId, BigDecimal unitCost, String offeredBrand, String offeredModel) {
    }
}
