package com.tdem.logistica.modules.proveedores.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Configuracion del modulo: plantillas de calificacion y requisitos de documentos (DEC-L070/L071). */
public final class ConfiguracionProveedores {

    private ConfiguracionProveedores() {
    }

    public record Datos(List<PlantillaDto> plantillas, List<Requisito> requisitos) {
    }

    public record Requisito(UUID requirementId, String name, String description, boolean required,
                            boolean hasExpiry, int sortOrder, boolean active) {
    }

    public record GuardarPlantilla(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 30) String code,
            @NotNull @Digits(integer = 3, fraction = 2) BigDecimal scaleMax,
            @NotNull @Digits(integer = 3, fraction = 2) BigDecimal approvalThreshold,
            @Min(1) @Max(60) int validityMonths,
            @Min(0) @Max(365) int alertDays,
            @NotEmpty(message = "La plantilla necesita al menos un criterio.")
            @Size(max = 40) List<@Valid CriterioPlantilla> criterios) {
    }

    public record CriterioPlantilla(
            UUID criterionId,
            @NotBlank(message = "Cada criterio necesita un nombre.") @Size(max = 250) String name,
            @NotNull @Digits(integer = 3, fraction = 2) BigDecimal weight,
            @Size(max = 10) List<@Valid OpcionPlantilla> opciones) {
    }

    public record OpcionPlantilla(
            @NotNull @Digits(integer = 3, fraction = 2) BigDecimal score,
            @NotBlank @Size(max = 250) String label) {
    }

    public record GuardarRequisitos(@NotNull @Size(max = 40) List<@Valid RequisitoInput> requisitos) {
    }

    public record RequisitoInput(
            UUID requirementId,
            @NotBlank(message = "Cada requisito necesita un nombre.") @Size(max = 150) String name,
            @Size(max = 500) String description,
            boolean required,
            boolean hasExpiry,
            boolean active) {
    }
}
