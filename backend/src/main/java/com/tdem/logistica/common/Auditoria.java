package com.tdem.logistica.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Auditoria de acciones criticas (RF-064) en log_cambios: quien, cuando,
 * sobre que entidad y el antes/despues (incluido el motivo). Se escribe en la
 * misma transaccion que la accion, asi no queda una sin la otra.
 */
@Component
public class Auditoria {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Auditoria(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void registrar(String entidad, UUID entidadId, String accion,
                          Map<String, ?> antes, Map<String, ?> despues, UUID usuarioId) {
        jdbc.update("""
                insert into log_cambios (entidad, entidad_id, accion, antes, despues, usuario_id)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)""",
                entidad, entidadId, accion, json(antes), json(despues), usuarioId);
    }

    private String json(Map<String, ?> valores) {
        return valores == null ? null : objectMapper.writeValueAsString(valores);
    }
}
