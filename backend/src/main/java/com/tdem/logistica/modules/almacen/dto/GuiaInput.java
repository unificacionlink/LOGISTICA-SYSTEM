package com.tdem.logistica.modules.almacen.dto;

import java.time.LocalDate;

public record GuiaInput(
        String series,
        String number,
        LocalDate issueDate) {
}
