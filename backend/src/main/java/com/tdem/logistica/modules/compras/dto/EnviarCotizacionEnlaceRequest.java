package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cotizacion que el proveedor envia desde su enlace (DEC-L052): precio por linea de nuestra RFQ. */
public record EnviarCotizacionEnlaceRequest(
        @NotBlank(message = "Selecciona la moneda.") @Size(max = 3) String currencyCode,
        @NotBlank(message = "Selecciona la condicion de pago.") @Size(max = 40) String paymentCondition,
        @NotBlank(message = "Selecciona la modalidad de entrega.") @Size(max = 30) String deliveryMode,
        @Min(value = 0, message = "El plazo no puede ser negativo.")
        @Max(value = 365, message = "El plazo debe ser de hasta 365 dias.") Integer leadDays,
        LocalDate expirationDate,
        @Size(max = 60, message = "El numero de cotizacion admite hasta 60 caracteres.") String supplierQuoteNumber,
        @NotEmpty(message = "Ingresa el precio de al menos un producto.")
        @Size(max = 200) @Valid List<Precio> precios) {

    public record Precio(
            @NotNull(message = "Falta el producto cotizado.") UUID rfqItemId,
            @NotNull(message = "El precio unitario es obligatorio.")
            @DecimalMin(value = "0.0001", message = "El precio unitario debe ser mayor a 0.")
            @Digits(integer = 9, fraction = 4, message = "Precio invalido: hasta 9 digitos y 4 decimales.")
            BigDecimal unitCost,
            @Size(max = 100, message = "La marca admite hasta 100 caracteres.") String offeredBrand,
            @Size(max = 120, message = "El modelo admite hasta 120 caracteres.") String offeredModel) {
    }
}
