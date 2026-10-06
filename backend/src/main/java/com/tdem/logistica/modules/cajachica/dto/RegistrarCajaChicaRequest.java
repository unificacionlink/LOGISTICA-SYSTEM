package com.tdem.logistica.modules.cajachica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistrarCajaChicaRequest(
        @NotBlank(message = "Indica el motivo.") String motivo,
        String motivoDetalle,
        @NotNull(message = "Indica la fecha de compra.") LocalDate fechaCompra,
        String proveedorRuc,
        @NotBlank(message = "Indica el proveedor.") String proveedorNombre,
        @NotBlank(message = "Indica el tipo de comprobante.") String tipoComprobante,
        String serie,
        @NotBlank(message = "Indica el numero del comprobante.") String numero,
        @NotBlank(message = "Indica la moneda.") String moneda,
        UUID documentId,
        String codigoExterno,
        @NotEmpty(message = "Indica al menos un item.") @Valid List<Item> items) {

    public record Item(
            @NotNull UUID requestItemId,
            @NotNull @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0.") BigDecimal qty,
            @NotNull @DecimalMin(value = "0", message = "El costo no puede ser negativo.") BigDecimal costoUnitario) {
    }
}
