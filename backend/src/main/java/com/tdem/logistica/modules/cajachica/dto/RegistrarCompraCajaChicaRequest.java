package com.tdem.logistica.modules.cajachica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Compra con la caja chica del proyecto (DEC-L092). La factura es obligatoria:
 * se adjunta como archivo en el mismo envio o se indica {@code facturaUrl}.
 */
public record RegistrarCompraCajaChicaRequest(
        @NotNull(message = "Elige el centro de costo (CECO).") UUID cecoId,
        @NotNull(message = "Indica la fecha de compra.") LocalDate fecha,
        @Size(max = 11, message = "El RUC tiene 11 dígitos.") String proveedorRuc,
        @NotBlank(message = "Indica dónde se compró.")
        @Size(max = 200, message = "El nombre admite hasta 200 caracteres.") String proveedorNombre,
        @NotBlank(message = "Indica el tipo de comprobante.") String tipoComprobante,
        @Size(max = 10, message = "La serie admite hasta 10 caracteres.") String serie,
        @NotBlank(message = "Indica el número del comprobante.")
        @Size(max = 20, message = "El número admite hasta 20 caracteres.") String numero,
        /** Enlace a la factura (Drive, correo, SUNAT...) cuando no se adjunta el archivo. */
        @Size(max = 500, message = "El enlace admite hasta 500 caracteres.") String facturaUrl,
        @Size(max = 500, message = "La nota admite hasta 500 caracteres.") String nota,
        @NotEmpty(message = "Agrega al menos una línea de lo comprado.")
        @Size(max = 50, message = "Una compra admite hasta 50 líneas.")
        @Valid List<Linea> lineas) {

    /** Linea libre o un producto del catalogo ({@code variantId}). */
    public record Linea(
            UUID variantId,
            @Size(max = 300, message = "La descripción admite hasta 300 caracteres.") String descripcion,
            @NotNull(message = "Indica la cantidad.") @Positive(message = "La cantidad debe ser mayor que cero.")
            @Digits(integer = 8, fraction = 3, message = "Cantidad inválida.") BigDecimal cantidad,
            @NotNull(message = "Indica el precio unitario.") @PositiveOrZero(message = "El precio no puede ser negativo.")
            @Digits(integer = 9, fraction = 4, message = "Precio inválido.") BigDecimal precioUnitario) {
    }
}
