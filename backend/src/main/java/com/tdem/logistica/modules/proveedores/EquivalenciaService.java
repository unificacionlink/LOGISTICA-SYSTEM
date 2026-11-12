package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.proveedores.dto.EquivalenciaDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Equivalencias aprendidas de cada proveedor (DEC-L053): se crean al guardar
 * una cotizacion en el comparativo; aqui solo se consultan y se quitan las que
 * quedaron mal.
 */
@Service
@Transactional(readOnly = true)
public class EquivalenciaService {

    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public EquivalenciaService(JdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    public List<EquivalenciaDto> listar(UUID supplierId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        return jdbc.query("""
                select e.equivalence_id, e.supplier_description, e.supplier_code, p.sku_base,
                       v.internal_variant_code, p.name, v.attribute_value, e.times_used, e.last_used_at
                  from proveedor_equivalencia e
                  join producto p on p.product_id = e.product_id
                  left join producto_variante v on v.variant_id = e.variant_id
                 where e.supplier_id = ?
                 order by p.sku_base, e.supplier_description""",
                (rs, n) -> {
                    Timestamp ultimo = rs.getTimestamp(9);
                    return new EquivalenciaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8),
                            ultimo != null ? ultimo.toInstant() : null);
                }, supplierId);
    }

    @Transactional
    public void quitar(UUID equivalenceId, UsuarioPrincipal principal) {
        exigirLogistica(principal);
        List<Map<String, Object>> fila = jdbc.queryForList("""
                select supplier_id, product_id, supplier_description from proveedor_equivalencia
                 where equivalence_id = ?""", equivalenceId);
        if (fila.isEmpty()) {
            throw ApiException.notFound("Equivalencia no encontrada.");
        }
        jdbc.update("delete from proveedor_equivalencia where equivalence_id = ?", equivalenceId);
        Map<String, Object> f = fila.get(0);
        auditoria.registrar("PROVEEDOR", (UUID) f.get("supplier_id"), "QUITAR_EQUIVALENCIA",
                Map.of("descripcion", String.valueOf(f.get("supplier_description")),
                        "producto", String.valueOf(f.get("product_id"))), null, principal.userId());
    }

    private static void exigirLogistica(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("No tienes permiso para gestionar proveedores.");
        }
    }
}
