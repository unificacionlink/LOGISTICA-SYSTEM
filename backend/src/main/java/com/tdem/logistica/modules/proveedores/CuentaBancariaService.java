package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.proveedores.dto.CuentaBancariaDto;
import com.tdem.logistica.modules.proveedores.dto.GuardarCuentaBancariaRequest;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cuentas bancarias del proveedor (DEC-L078). Dato sensible: solo Logistica y
 * Admin las ven y editan, y la auditoria guarda el numero enmascarado (los 4
 * ultimos digitos), nunca completo.
 */
@Service
@Transactional(readOnly = true)
public class CuentaBancariaService {

    private static final String SELECT = """
            select bank_account_id, bank_name, account_type, currency_code, account_number, cci, is_primary, created_at
              from proveedor_cuenta_bancaria""";

    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public CuentaBancariaService(JdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    public List<CuentaBancariaDto> listar(UUID supplierId, UsuarioPrincipal principal) {
        AccesoProveedores.exigir(principal);
        exigirProveedor(supplierId);
        return jdbc.query(SELECT + " where supplier_id = ? order by is_primary desc, account_type = 'DETRACCION', bank_name",
                (rs, i) -> mapear(rs), supplierId);
    }

    @Transactional
    public List<CuentaBancariaDto> crear(UUID supplierId, GuardarCuentaBancariaRequest r, UsuarioPrincipal principal) {
        AccesoProveedores.exigir(principal);
        exigirProveedor(supplierId);
        Datos d = datos(r);
        if (d.principal()) {
            quitarPrincipal(supplierId);
        }
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    insert into proveedor_cuenta_bancaria (supplier_id, bank_name, account_type, currency_code,
                      account_number, cci, is_primary, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?) returning bank_account_id""", UUID.class,
                    supplierId, d.banco(), d.tipo(), d.moneda(), d.numero(), d.cci(), d.principal(), principal.userId());
        } catch (DuplicateKeyException e) {
            throw ApiException.badRequest("Esa cuenta ya esta registrada para el proveedor.");
        }
        auditoria.registrar("PROVEEDOR", supplierId, "AGREGAR_CUENTA_BANCARIA", null, resumen(d, id), principal.userId());
        return listar(supplierId, principal);
    }

    @Transactional
    public List<CuentaBancariaDto> actualizar(UUID cuentaId, GuardarCuentaBancariaRequest r, UsuarioPrincipal principal) {
        AccesoProveedores.exigir(principal);
        Map<String, Object> actual = cuenta(cuentaId);
        UUID supplierId = (UUID) actual.get("supplier_id");
        Datos d = datos(r);
        if (d.principal()) {
            quitarPrincipal(supplierId);
        }
        try {
            jdbc.update("""
                    update proveedor_cuenta_bancaria set bank_name = ?, account_type = ?, currency_code = ?,
                      account_number = ?, cci = ?, is_primary = ?, updated_at = now(), updated_by = ?
                    where bank_account_id = ?""",
                    d.banco(), d.tipo(), d.moneda(), d.numero(), d.cci(), d.principal(), principal.userId(), cuentaId);
        } catch (DuplicateKeyException e) {
            throw ApiException.badRequest("Esa cuenta ya esta registrada para el proveedor.");
        }
        Datos antes = new Datos((String) actual.get("bank_name"), (String) actual.get("account_type"),
                (String) actual.get("currency_code"), (String) actual.get("account_number"), (String) actual.get("cci"),
                (Boolean) actual.get("is_primary"));
        auditoria.registrar("PROVEEDOR", supplierId, "EDITAR_CUENTA_BANCARIA", resumen(antes, cuentaId),
                resumen(d, cuentaId), principal.userId());
        return listar(supplierId, principal);
    }

    @Transactional
    public List<CuentaBancariaDto> eliminar(UUID cuentaId, UsuarioPrincipal principal) {
        AccesoProveedores.exigir(principal);
        Map<String, Object> actual = cuenta(cuentaId);
        UUID supplierId = (UUID) actual.get("supplier_id");
        jdbc.update("delete from proveedor_cuenta_bancaria where bank_account_id = ?", cuentaId);
        Datos antes = new Datos((String) actual.get("bank_name"), (String) actual.get("account_type"),
                (String) actual.get("currency_code"), (String) actual.get("account_number"), (String) actual.get("cci"),
                (Boolean) actual.get("is_primary"));
        auditoria.registrar("PROVEEDOR", supplierId, "QUITAR_CUENTA_BANCARIA", resumen(antes, cuentaId), null,
                principal.userId());
        return listar(supplierId, principal);
    }

    /** Numero enmascarado para la auditoria: solo los 4 ultimos digitos. */
    static String enmascarar(String numero) {
        if (numero == null) {
            return null;
        }
        String digitos = numero.replaceAll("\\D", "");
        return digitos.length() <= 4 ? "****" : "****" + digitos.substring(digitos.length() - 4);
    }

    // ---------------------------------------------------------------- utilidades

    private record Datos(String banco, String tipo, String moneda, String numero, String cci, boolean principal) {
    }

    private Datos datos(GuardarCuentaBancariaRequest r) {
        String moneda = r.moneda().trim().toUpperCase(Locale.ROOT);
        Integer existe = jdbc.queryForObject("select count(*) from moneda where currency_code = ? and active",
                Integer.class, moneda);
        if (existe == null || existe == 0) {
            throw ApiException.badRequest("Moneda no valida.");
        }
        String cci = AccesoProveedores.limpio(r.cci());
        return new Datos(r.banco().trim().toUpperCase(Locale.ROOT), r.tipo(), moneda,
                r.numero().trim().replaceAll("\\s+", " "), cci == null ? null : cci.replaceAll("\\s+", " "),
                r.principal());
    }

    private static Map<String, Object> resumen(Datos d, UUID id) {
        return Map.of("cuenta", id.toString(), "banco", d.banco(), "tipo", d.tipo(), "moneda", d.moneda(),
                "numero", enmascarar(d.numero()), "cci", d.cci() == null ? "-" : enmascarar(d.cci()),
                "principal", d.principal());
    }

    private void quitarPrincipal(UUID supplierId) {
        jdbc.update("update proveedor_cuenta_bancaria set is_primary = false where supplier_id = ? and is_primary",
                supplierId);
    }

    private void exigirProveedor(UUID supplierId) {
        Integer n = jdbc.queryForObject("select count(*) from proveedor where supplier_id = ?", Integer.class, supplierId);
        if (n == null || n == 0) {
            throw ApiException.notFound("Proveedor no encontrado.");
        }
    }

    private Map<String, Object> cuenta(UUID cuentaId) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select * from proveedor_cuenta_bancaria where bank_account_id = ? for update", cuentaId);
        if (filas.isEmpty()) {
            throw ApiException.notFound("Cuenta bancaria no encontrada.");
        }
        return filas.get(0);
    }

    private static CuentaBancariaDto mapear(ResultSet rs) throws SQLException {
        return new CuentaBancariaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getTimestamp(8).toInstant());
    }
}
