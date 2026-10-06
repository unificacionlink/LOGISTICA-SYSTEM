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
        List<Item> items) {

    public record Item(String sku, String producto, BigDecimal cantidad, BigDecimal costoUnitario, BigDecimal total) {
    }
}
