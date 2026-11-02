package com.tdem.logistica.modules.maestro;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.maestro.dto.PrecioVentaDto;
import com.tdem.logistica.modules.maestro.dto.PreciosVentaDto;
import com.tdem.logistica.modules.stock.dto.EmpresaDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Precio de venta por empresa y plazo de atencion por SKU (Lote 13, DEC-L035;
 * catalogo-precios.js y plazos-atencion.js de la V38.7).
 * <ul>
 *   <li>Precio: lo registran solo PYC y LOGISTICA (quien conoce los costos),
 *       en las empresas de su ambito; GERENCIA y ADMIN solo lo ven. Uno vigente
 *       por empresa + SKU; registrar uno nuevo desactiva el anterior (queda de
 *       historial) y se audita.</li>
 *   <li>Plazo: dias calendario por SKU (0-3650 o sin configurar), solo
 *       LOGISTICA/ADMIN. Se muestra en el catalogo y al armar el requerimiento.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PrecioPlazoService {

    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public PrecioPlazoService(JdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- precios de venta

    public PreciosVentaDto listarPrecios(UUID companyId, UsuarioPrincipal principal) {
        List<EmpresaDto> empresas = empresasDePrecios(principal);
        UUID empresa = companyId != null ? exigirEmpresa(companyId, empresas) : empresaPorDefecto(empresas, principal);
        List<PrecioVentaDto> precios = jdbc.query("""
                select pv.product_id, pv.sale_price, u.display_name, pv.created_at
                from precio_venta_producto pv join usuario u on u.user_id = pv.created_by
                where pv.company_id = ? and pv.active""", (rs, i) -> mapear(rs), empresa);
        return new PreciosVentaDto(empresas, empresa, precios);
    }

    @Transactional
    public PrecioVentaDto registrarPrecio(UUID productId, UUID companyId, BigDecimal precio, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.PYC, Roles.LOGISTICA)) {
            throw ApiException.forbidden("Solo PYC y Logistica registran precios de venta.");
        }
        exigirEmpresa(companyId, empresasDePrecios(principal));
        if (precio == null || precio.signum() <= 0) {
            throw ApiException.badRequest("Ingresa un precio de venta mayor que cero.");
        }
        // Bloquea el SKU: dos registros simultaneos no deben dejar dos precios vigentes.
        bloquearProductoActivo(productId);
        List<BigDecimal> vigente = jdbc.queryForList("""
                select sale_price from precio_venta_producto where company_id = ? and product_id = ? and active""",
                BigDecimal.class, companyId, productId);
        BigDecimal anterior = vigente.isEmpty() ? null : vigente.get(0);
        if (anterior != null && anterior.compareTo(precio) == 0) {
            return vigente(companyId, productId);
        }
        jdbc.update("update precio_venta_producto set active = false where company_id = ? and product_id = ? and active",
                companyId, productId);
        jdbc.update("""
                insert into precio_venta_producto (company_id, product_id, sale_price, created_by)
                values (?, ?, ?, ?)""", companyId, productId, precio, principal.userId());

        Map<String, Object> antes = new HashMap<>();
        antes.put("empresa", companyId);
        antes.put("precio", anterior);
        auditoria.registrar("PRODUCTO", productId, "PRECIO_VENTA", antes,
                Map.of("empresa", companyId, "precio", precio), principal.userId());
        return vigente(companyId, productId);
    }

    /** Empresas visibles: LOGISTICA/ADMIN todas las activas; PYC/GERENCIA la suya y las de sus CECOs. */
    private List<EmpresaDto> empresasDePrecios(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.PYC, Roles.GERENCIA, Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("Sin permiso para ver precios de venta.");
        }
        if (principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            return jdbc.query("select company_id, code from empresa where active order by code",
                    (rs, i) -> new EmpresaDto(rs.getObject(1, UUID.class), rs.getString(2)));
        }
        return jdbc.query("""
                select e.company_id, e.code from empresa e
                where e.active and (
                  e.company_id = (select company_id from usuario where user_id = ?)
                  or exists (select 1 from usuario_ceco uc join ceco c on c.ceco_id = uc.ceco_id
                             where uc.user_id = ? and c.company_id = e.company_id))
                order by e.code""",
                (rs, i) -> new EmpresaDto(rs.getObject(1, UUID.class), rs.getString(2)),
                principal.userId(), principal.userId());
    }

    private static UUID exigirEmpresa(UUID companyId, List<EmpresaDto> empresas) {
        if (companyId == null || empresas.stream().noneMatch(e -> e.companyId().equals(companyId))) {
            throw ApiException.forbidden("Sin permiso para registrar precios en esta empresa.");
        }
        return companyId;
    }

    /** La empresa del usuario si esta en su ambito; si no, la primera. */
    private UUID empresaPorDefecto(List<EmpresaDto> empresas, UsuarioPrincipal principal) {
        if (empresas.isEmpty()) {
            throw ApiException.forbidden("Tu usuario no tiene empresa asignada.");
        }
        List<UUID> propia = jdbc.queryForList("select company_id from usuario where user_id = ?", UUID.class,
                principal.userId());
        return empresas.stream().map(EmpresaDto::companyId)
                .filter(id -> !propia.isEmpty() && id.equals(propia.get(0)))
                .findFirst().orElse(empresas.get(0).companyId());
    }

    private PrecioVentaDto vigente(UUID companyId, UUID productId) {
        return jdbc.query("""
                select pv.product_id, pv.sale_price, u.display_name, pv.created_at
                from precio_venta_producto pv join usuario u on u.user_id = pv.created_by
                where pv.company_id = ? and pv.product_id = ? and pv.active""",
                (rs, i) -> mapear(rs), companyId, productId).getFirst();
    }

    private static PrecioVentaDto mapear(ResultSet rs) throws SQLException {
        Timestamp en = rs.getTimestamp(4);
        return new PrecioVentaDto(rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getString(3),
                en != null ? en.toInstant() : null);
    }

    // ---------------------------------------------------------------- plazo de atencion

    @Transactional
    public Integer configurarPlazo(UUID productId, Integer dias, UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("Solo Logistica configura el plazo de atencion.");
        }
        if (dias != null && (dias < 0 || dias > 3650)) {
            throw ApiException.badRequest("Ingresa dias calendario entre 0 y 3650, o deja vacio.");
        }
        Integer anterior = bloquearProductoActivo(productId);
        if (Objects.equals(anterior, dias)) {
            return dias;
        }
        jdbc.update("""
                update producto set attention_days = ?, attention_days_updated_by = ?, attention_days_updated_at = now()
                where product_id = ?""", dias, principal.userId(), productId);
        Map<String, Object> antes = new HashMap<>();
        antes.put("dias", anterior);
        Map<String, Object> despues = new HashMap<>();
        despues.put("dias", dias);
        auditoria.registrar("PRODUCTO", productId, "PLAZO_ATENCION", antes, despues, principal.userId());
        return dias;
    }

    /** Bloquea el SKU activo y devuelve su plazo actual (puede ser null). */
    private Integer bloquearProductoActivo(UUID productId) {
        List<Map<String, Object>> p = jdbc.queryForList(
                "select attention_days from producto where product_id = ? and active for update", productId);
        if (p.isEmpty()) {
            throw ApiException.badRequest("Selecciona un SKU activo del catalogo.");
        }
        return (Integer) p.get(0).get("attention_days");
    }
}
