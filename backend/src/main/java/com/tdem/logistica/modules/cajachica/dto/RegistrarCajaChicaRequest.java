package com.tdem.logistica.modules.cajachica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Compra con la caja chica del proyecto (DEC-L054). El motivo no se elige: sale
 * de la ruta que autorizo Logistica. El comprobante adjunto es obligatorio.
 */
public record RegistrarCajaChicaRequest(
        @NotNull(message = "Indica la fecha de compra.") LocalDate fechaCompra,
        @Size(max = 20, message = "RUC invalido.") String proveedorRuc,
        @NotBlank(message = "Indica el proveedor.") @Size(max = 200) String proveedorNombre,
        @NotBlank(message = "Indica el tipo de comprobante.") String tipoComprobante,
        @Size(max = 20) String serie,
        @NotBlank(message = "Indica el numero del comprobante.") @Size(max = 40) String numero,
        @NotBlank(message = "Indica la moneda.") String moneda,
        /** Obligatorio al registrar; al corregir, null conserva el comprobante anterior. */
        UUID documentId,
        @NotEmpty(message = "Indica al menos un item.") @Valid List<Item> items) {

    public record Item(
            @NotNull UUID requestItemId,
            @NotNull @DecimalMin(value = "1", message = "La cantidad debe ser al menos 1.") @Digits(integer = 6, fraction = 0, message = "La cantidad debe ser un numero entero de hasta 6 digitos.") BigDecimal qty,
            @NotNull @DecimalMin(value = "0", message = "El costo no puede ser negativo.") @Digits(integer = 9, fraction = 4, message = "Costo invalido: hasta 9 digitos y 4 decimales.") BigDecimal costoUnitario) {
    }
}
