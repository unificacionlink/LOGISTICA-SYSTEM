package com.tdem.logistica.modules.stock.dto;

import java.math.BigDecimal;

public record SaldoAjustadoDto(String codigoVariante, BigDecimal saldoDisponible) {
}
