package com.tdem.logistica.modules.identidad.dto;

import java.time.Instant;
import java.util.List;

/** Todo lo que necesita la pantalla de Administracion, en una sola respuesta. */
public record AdminDatosDto(
        List<UsuarioAdminDto> usuarios,
        List<EmpresaAdminDto> empresas,
        List<CecoAdminDto> cecos,
        List<String> roles,
        boolean syncConfigurado,
        Instant ultimaSincronizacion,
        SyncResultadoDto ultimoResultado) {
}
