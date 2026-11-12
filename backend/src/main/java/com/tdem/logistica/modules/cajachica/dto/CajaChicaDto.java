package com.tdem.logistica.modules.cajachica.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CajaChicaDto(
        UUID id,
        String codigo,
        UUID requestId,
        String requestCode,
        String empresaCode,
        LocalDate fechaCompra,
        String proveedor,
        String proveedorRuc,
        String comprobante,
        String moneda,
        BigDecimal monto,
        String motivo,
        String motivoDetalle,
        String estado,
        String observacion,
        String codigoExterno,
        String documentoUrl,
        String registradoPor,
        Instant registradoEn,
        /** PROYECTO (DEC-L054) o ALMACEN (compras anteriores, entraron al stock). */
        String destino,
        List<Item> items) {

    public record Item(UUID requestItemId, String sku, String producto, BigDecimal cantidad, BigDecimal costoUnitario,
                       BigDecimal total) {
    }
}
