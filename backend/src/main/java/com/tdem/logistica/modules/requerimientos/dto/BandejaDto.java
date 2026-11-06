package com.tdem.logistica.modules.requerimientos.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bandeja operativa de Requerimientos (DEC-L048): vistas con su conteo, conteo
 * por empresa dentro de la vista y una pagina de filas con su avance y las
 * acciones que el usuario tiene pendientes. Todo calculado en el servidor.
 */
public record BandejaDto(
        String vista,
        List<Vista> vistas,
        List<Empresa> empresas,
        int total,
        int pagina,
        int porPagina,
        List<Fila> filas) {

    public record Vista(String id, String label, int total) {
    }

    public record Empresa(String code, int total) {
    }

    /**
     * @param avance fracciones 0..1 promediadas por producto (no mezcla unidades de medida)
     * @param acciones ordenadas: primero lo que el usuario puede hacer, luego lo que espera de otros
     */
    public record Fila(
            UUID id,
            String code,
            String empresaCode,
            String cecoCode,
            String cecoNombre,
            UUID requesterUserId,
            String solicitante,
            String priority,
            String status,
            LocalDate requiredDate,
            OffsetDateTime createdAt,
            int itemsCount,
            int itemsEntregados,
            String resumenItems,
            Avance avance,
            List<Accion> acciones) {
    }

    public record Avance(double entregado, double enCamino, double listo, double enCompra) {
    }

    /**
     * @param espera true = depende de otro (proveedor, aprobador, destino); no es trabajo del usuario ahora
     * @param destino id que necesita la pantalla de la accion (REQ, consolidados separados por coma, OC o NI)
     */
    public record Accion(String codigo, boolean espera, String destino) {
    }
}
