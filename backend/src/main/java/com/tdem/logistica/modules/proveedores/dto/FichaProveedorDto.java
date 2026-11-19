package com.tdem.logistica.modules.proveedores.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ficha del proveedor: datos, clasificacion, productos, ultima calificacion y advertencias (DEC-L069). */
public record FichaProveedorDto(
        ProveedorDto proveedor,
        List<Categoria> categorias,
        List<ProductoOfrecido> productos,
        EvaluacionResumenDto ultimaSeleccion,
        EvaluacionResumenDto ultimaEvaluacion,
        String vigencia,
        LocalDate proximaEvaluacion,
        /** Lo que conviene revisar antes de habilitar; habilitar con advertencias exige motivo. */
        List<String> advertencias) {

    public record Categoria(UUID productTypeId, UUID familyId, String tipo, String familia) {
    }

    public record ProductoOfrecido(UUID productId, String skuBase, String nombre, String tipo, String familia) {
    }
}
