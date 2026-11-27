package com.tdem.logistica.modules.reportes.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reportes de observaciones desde la pantalla (DEC-L104). */
public final class ReporteObservacionDto {

    private ReporteObservacionDto() {
    }

    /** Lo que devuelve el envio: el numero que ve el usuario (OBS-0001). */
    public record Creado(UUID id, int numero) {
    }

    /** Fila completa para el administrador. */
    public record Fila(UUID id, int numero, Instant en, UUID usuarioId, String usuario, String correo, String roles,
                       String modulo, String pantalla, String ruta, String zona, String descripcion,
                       boolean tieneCaptura, String navegador, String viewport, String estado, String respuesta,
                       String revisadoPor, Instant revisadoEn) {
    }

    /** Lo que ve quien reporto de sus propios reportes: estado y respuesta. */
    public record Propio(UUID id, int numero, Instant en, String pantalla, String zona, String descripcion,
                         String estado, String respuesta) {
    }

    public record Pagina(List<Fila> filas, boolean hayMas, Conteo conteo) {
    }

    /** Totales por estado (sin filtro de estado ni texto). */
    public record Conteo(long pendientes, long enRevision, long resueltos, long descartados) {
    }
}
