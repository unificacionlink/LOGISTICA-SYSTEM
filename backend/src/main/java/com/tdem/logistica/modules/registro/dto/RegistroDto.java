package com.tdem.logistica.modules.registro.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Vistas del registro de la plataforma para el administrador (DEC-L060). */
public final class RegistroDto {

    private RegistroDto() {
    }

    /** Una pagina de filas, de la mas reciente a la mas antigua. */
    public record Pagina<T>(List<T> filas, boolean hayMas) {
    }

    public record Actividad(UUID id, Instant en, UUID usuarioId, String usuario, String correo, String metodo,
                            String ruta, String modulo, UUID entidadId, int status, int ms, String ip,
                            String navegador, String detalle) {
    }

    public record Error(UUID id, Instant en, String origen, UUID usuarioId, String usuario, String metodo,
                        String ruta, Integer status, String tipo, String mensaje, String pila, String navegador) {
    }

    /** Accion critica de log_cambios (RF-064); antes y despues van como JSON. */
    public record Cambio(UUID id, Instant en, UUID usuarioId, String usuario, String entidad, UUID entidadId,
                         String accion, String antes, String despues) {
    }

    /** Movimiento del kardex (movimiento_inventario). */
    public record Movimiento(UUID id, Instant en, UUID usuarioId, String usuario, String empresa, String almacen,
                             String codigo, String producto, String tipo, BigDecimal cantidad,
                             BigDecimal costoUnitario, String referenciaTipo, UUID referenciaId) {
    }

    /** Ultimas 24 horas. */
    public record Resumen(long operaciones, long rechazadas, long ingresosFallidos, long erroresServidor,
                          long erroresNavegador) {
    }
}
