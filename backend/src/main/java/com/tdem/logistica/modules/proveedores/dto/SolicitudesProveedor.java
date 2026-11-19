package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Cuerpos y respuestas pequenas del ciclo y de la ficha del proveedor (DEC-L069). */
public final class SolicitudesProveedor {

    private SolicitudesProveedor() {
    }

    /** Decision sobre una solicitud en revision. */
    public record Decision(
            @NotNull @Pattern(regexp = "HABILITAR|OBSERVAR|RECHAZAR", message = "Decision invalida.") String decision,
            @Size(max = 1000) String motivo) {
    }

    public record Motivo(@Size(max = 1000) String motivo) {
    }

    public record EstadoActivo(boolean activo, @Size(max = 1000) String motivo) {
    }

    public record Categorias(@NotNull @Size(max = 100) List<@Valid CategoriaItem> items) {
    }

    /** familyId nulo = toda la familia del catalogo (tipo de producto). */
    public record CategoriaItem(@NotNull UUID productTypeId, UUID familyId) {
    }

    public record Productos(@NotNull @Size(max = 2000) List<UUID> productIds) {
    }

    /** Producto del catalogo que se puede asignar a un proveedor. */
    public record ProductoDisponible(UUID productId, String skuBase, String nombre, String tipo, String familia) {
    }

    /** Una accion registrada sobre el proveedor (log_cambios, RF-064). */
    public record Historial(String accion, String estadoAntes, String estadoDespues, String motivo,
                            String usuario, OffsetDateTime fecha) {
    }
}
