package com.tdem.logistica.modules.tracking.dto;

import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * RF-039/081/083: una sola vista del Tracking que alimenta la pantalla y el
 * Excel. Cabecera del REQ + hitos calculados + una fila por NI/envio + historial.
 */
public record TrackingDto(
        RequerimientoDto requerimiento,
        String cecoCodigo,
        LocalDate entregaProgramada,
        String direccionEnvio,
        BigDecimal valorPedido,
        BigDecimal unidadesSolicitadas,
        BigDecimal unidadesEntregadas,
        int porcentajeAtencion,
        List<HitoDto> hitos,
        List<EntregaDto> entregas,
        List<EventoDto> eventos) {
}
