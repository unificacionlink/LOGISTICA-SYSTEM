package com.tdem.logistica.modules.compras.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * F-LOG-08 de una OC (DEC-L102). {@code disponible}: ya se recibio todo lo de la OC
 * (si no, {@code motivo} dice por que). {@code registrada}: Logistica ya la guardo.
 */
public record VerificacionOcDto(
        UUID orderId,
        String orderCode,
        boolean disponible,
        String motivo,
        boolean registrada,
        boolean puedeEditar,
        Instant verificadaEn,
        Persona verificadaPor,
        Persona especialista,
        UUID especialistaSugeridoId,
        String conclusion,
        List<Item> items,
        List<NotaRef> notas,
        List<Persona> candidatos) {

    /** Y = conforme, F = no conforme, null = no aplica. {@code producto}: pasa por el almacen. */
    public record Item(UUID orderItemId, String descripcion, String um, BigDecimal cantidad, BigDecimal recibida,
                       boolean producto, String funcionalidad, String estandares, String especificaciones,
                       String observacion) {
    }

    public record Persona(UUID id, String nombre, String cargo, boolean tieneFirma) {
    }

    /** Nota de inventario que despacha lo comprado en esta OC. */
    public record NotaRef(UUID id, String codigo, String estado) {
    }
}
