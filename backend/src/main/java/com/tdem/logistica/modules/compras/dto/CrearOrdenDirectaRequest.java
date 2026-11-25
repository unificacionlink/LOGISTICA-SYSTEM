package com.tdem.logistica.modules.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * OC directa (DEC-L080): sin cotizacion. CECO y requerimiento son referencias
 * opcionales, de cualquiera de las tres empresas: la orden sale siempre a
 * nombre de TDEM, que compra para el grupo (DEC-L086). Cada linea es un
 * producto del catalogo ({@code variantId}) o un servicio (sin variante, con
 * descripcion).
 */
public record CrearOrdenDirectaRequest(
        @NotNull(message = "Selecciona el proveedor.") UUID supplierId,
        @NotBlank(message = "Selecciona la moneda.") String currencyCode,
        @NotBlank(message = "Selecciona la forma de pago.") String paymentCondition,
        @NotBlank(message = "Selecciona la modalidad de entrega.") String deliveryMode,
        @Size(max = 220, message = "El lugar de entrega admite hasta 220 caracteres.") String deliveryLocation,
        @Size(max = 120, message = "El plazo de entrega admite hasta 120 caracteres.") String deliveryTerm,
        @NotBlank(message = "Indica como se aplica el IGV.") String taxMode,
        @Size(max = 2000, message = "Las especificaciones admiten hasta 2000 caracteres.") String additionalSpecs,
        UUID cecoId,
        /** Codigo del requerimiento de referencia (REQ-...), opcional. */
        @Size(max = 50, message = "El codigo del requerimiento admite hasta 50 caracteres.") String requestCode,
        @NotEmpty(message = "Agrega al menos una linea.")
        @Size(max = 100, message = "Una orden admite hasta 100 lineas.")
        @Valid List<Linea> lineas,
        /** Entrega directa del proveedor al CECO de la orden (DEC-L084); exige el CECO. */
        Boolean entregaDirecta) {

    public record Linea(
            UUID variantId,
            @Size(max = 250, message = "La descripcion admite hasta 250 caracteres.") String descripcion,
            @NotNull(message = "Indica la cantidad.")
            @Positive(message = "La cantidad debe ser mayor a cero.")
            @Digits(integer = 9, fraction = 0, message = "La cantidad es un numero entero.") BigDecimal cantidad,
            @NotNull(message = "Indica el precio unitario.")
            @Positive(message = "El precio unitario debe ser mayor a cero.")
            @Digits(integer = 10, fraction = 4, message = "El precio admite hasta 4 decimales.") BigDecimal precioUnitario) {
    }
}
