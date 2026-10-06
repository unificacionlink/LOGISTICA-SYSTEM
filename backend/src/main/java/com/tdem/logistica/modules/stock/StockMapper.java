package com.tdem.logistica.modules.stock;

import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.modules.stock.dto.EmpresaDto;

/** Equivalente a *.mapper.ts: fila (entidad) <-> DTO, a mano (sin MapStruct). */
public final class StockMapper {

    private StockMapper() {
    }

    public static EmpresaDto toDto(Empresa e) {
        return new EmpresaDto(e.getCompanyId(), e.getCode());
    }
}
