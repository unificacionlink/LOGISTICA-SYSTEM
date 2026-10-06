package com.tdem.logistica.modules.requerimientos.dto;

import java.util.List;

public record CatalogoFormularioDto(
        List<VarianteCatalogoDto> variantes, List<FamiliaDto> familias, List<CecoDto> cecos) {
}
