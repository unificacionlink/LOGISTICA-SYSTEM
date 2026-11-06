package com.tdem.logistica.modules.requerimientos;

import com.tdem.logistica.entity.FamiliaProducto;
import com.tdem.logistica.entity.ProductoVariante;
import com.tdem.logistica.entity.Requerimiento;
import com.tdem.logistica.entity.RequerimientoItem;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.modules.requerimientos.dto.CecoDto;
import com.tdem.logistica.modules.requerimientos.dto.FamiliaDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoItemDto;
import com.tdem.logistica.modules.requerimientos.dto.VarianteCatalogoDto;

/** Equivalente a *.mapper.ts: fila (entidad) <-> DTO, a mano (sin MapStruct). */
public final class RequerimientoMapper {

    private RequerimientoMapper() {
    }

    public static RequerimientoDto toDto(Requerimiento r) {
        String cecoNombre = r.getCeco() != null ? r.getCeco().getCode() + " — " + r.getCeco().getName() : null;
        String requesterNombre = r.getRequester() != null ? r.getRequester().getDisplayName() : null;
        return new RequerimientoDto(
                r.getRequestId(),
                r.getRequestCode(),
                r.getRequester() != null ? r.getRequester().getUserId() : null,
                r.getCeco() != null ? r.getCeco().getCecoId() : null,
                cecoNombre,
                requesterNombre,
                r.getPriority(),
                r.getPurpose(),
                r.getJustification(),
                r.getStatus(),
                r.getRequiredDate(),
                r.getScheduledDeliveryDate(),
                r.getCreatedAt(),
                r.getEmpresa() != null ? r.getEmpresa().getCode() : null,
                null,
                null);
    }

    public static RequerimientoItemDto toDto(RequerimientoItem i) {
        return new RequerimientoItemDto(
                i.getRequestItemId(),
                i.getProducto().getSkuBase(),
                i.getProducto().getName(),
                i.getVariante() != null ? i.getVariante().getAttributeValue() : null,
                i.getVariante() != null ? i.getVariante().getInternalVariantCode() : null,
                i.getUom().getCode(),
                i.getQtyRequested(),
                i.getTechnicalRequirement(),
                i.getObservation());
    }

    public static VarianteCatalogoDto toDto(ProductoVariante v, String imagenUrl) {
        String nombre = v.getProducto().getName();
        String atributo = v.getAttributeValue();
        String etiqueta = v.getInternalVariantCode() + " — " + nombre + (atributo != null ? " (" + atributo + ")" : "");
        return new VarianteCatalogoDto(
                v.getVariantId(),
                v.getProducto().getProductId(),
                v.getProducto().getSkuBase(),
                nombre,
                atributo,
                v.getInternalVariantCode(),
                etiqueta,
                v.getProducto().getFamilia().getFamilyId(),
                v.getProducto().getFamilia().getName(),
                v.getProducto().getFamilia().getTipo().getProductTypeId(),
                v.getProducto().getFamilia().getTipo().getName(),
                imagenUrl,
                v.getProducto().getAttentionDays());
    }

    public static FamiliaDto toDto(FamiliaProducto f) {
        return new FamiliaDto(f.getFamilyId(), f.getCode(), f.getName(),
                f.getTipo().getProductTypeId(), f.getTipo().getCode(), f.getTipo().getName());
    }

    public static CecoDto toDto(Ceco c) {
        return new CecoDto(c.getCecoId(), c.getCode(), c.getName(), c.getEmpresa().getCode());
    }
}
