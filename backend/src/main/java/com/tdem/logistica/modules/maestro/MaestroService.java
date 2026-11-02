package com.tdem.logistica.modules.maestro;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.modules.maestro.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;

/**
 * Maestro de productos (Lote 8b, DEC-L025; RF-004).
 * <ul>
 *   <li>SKU = tipo (2) + familia (2) + correlativo de 3 digitos dentro de la
 *       familia (p. ej. AC01007), el mismo formato de los 725 SKU sembrados.
 *       La familia se bloquea mientras se calcula el correlativo.</li>
 *   <li>Variante = SKU + "-" + codigo de atributo; "-00" si no tiene
 *       atributo (EP01027-L, AC01001-00).</li>
 *   <li>Solo LOGISTICA/ADMIN crean productos. El solicitante pide el alta
 *       ({@code solicitud_maestro_producto}); Logistica la aprueba (y crea el
 *       SKU) o la rechaza con motivo.</li>
 * </ul>
 * Escribe con SQL directo: las entidades JPA de producto solo mapean lo que
 * leen los modulos (no el tipo ni la subfamilia).
 */
@Service
@Transactional(readOnly = true)
public class MaestroService {

    static final Set<String> TIPOS_ATRIBUTO = Set.of("TALLA", "COLOR", "MODELO", "OTRO");
    private static final String SIN_ATRIBUTO = "00";

    private final JdbcTemplate jdbc;

    public MaestroService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static boolean esGestor(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
    }

    private static void exigirGestor(UsuarioPrincipal p) {
        if (!esGestor(p)) {
            throw ApiException.forbidden("Solo Logistica puede gestionar el maestro de productos.");
        }
    }

    // ---------------------------------------------------------------- clasificacion

    public ClasificacionDto clasificacion() {
        Map<UUID, List<ClasificacionDto.Familia>> familias = new LinkedHashMap<>();
        jdbc.query("""
                select f.family_id, f.product_type_id, f.code, f.name from familia_producto f
                where f.active order by f.code""", rs -> {
            familias.computeIfAbsent(rs.getObject(2, UUID.class), k -> new ArrayList<>())
                    .add(new ClasificacionDto.Familia(rs.getObject(1, UUID.class), rs.getString(3), rs.getString(4)));
        });
        List<ClasificacionDto.Tipo> tipos = jdbc.query(
                "select product_type_id, code, name from tipo_producto where active order by name",
                (rs, i) -> new ClasificacionDto.Tipo(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        familias.getOrDefault(rs.getObject(1, UUID.class), List.of())));
        // ND es un placeholder del maestro origen: no se ofrece para altas nuevas.
        List<ClasificacionDto.Unidad> unidades = jdbc.query(
                "select uom_id, code, name from unidad_medida where active and code <> 'ND' order by name",
                (rs, i) -> new ClasificacionDto.Unidad(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
        return new ClasificacionDto(tipos, unidades);
    }

    // ---------------------------------------------------------------- productos

    @Transactional
    public ProductoCreadoDto crearProducto(CrearProductoRequest r, UsuarioPrincipal principal) {
        exigirGestor(principal);
        return crear(r.familyId(), r.nombre(), r.uomId(), r.variantes(), principal.userId(), null);
    }

    /** Agrega una variante (p. ej. una talla nueva) a un producto que ya maneja ese atributo. */
    @Transactional
    public ProductoCreadoDto agregarVariante(UUID productId, VarianteInput v, UsuarioPrincipal principal) {
        exigirGestor(principal);
        Map<String, Object> p = producto(productId);
        String sku = (String) p.get("sku_base");
        List<String> tiposActuales = jdbc.queryForList(
                "select distinct coalesce(attribute_type, '') from producto_variante where product_id = ?", String.class, productId);
        String tipo = tipoAtributo(v.tipo());
        if (tiposActuales.contains("")) {
            throw ApiException.badRequest(sku + " no maneja atributos (tallas, colores...). Crea un producto nuevo.");
        }
        if (!tiposActuales.contains(tipo)) {
            throw ApiException.badRequest(sku + " maneja " + String.join(", ", tiposActuales).toLowerCase(Locale.ROOT)
                    + ", no " + tipo.toLowerCase(Locale.ROOT) + ".");
        }
        insertarVariante(productId, sku, tipo, v.valor());
        return creado(productId);
    }

    /** Edicion basica: nombre, unidad y activo/inactivo (un inactivo sale del buscador de requerimientos). */
    @Transactional
    public ProductoCreadoDto editarProducto(UUID productId, String nombre, UUID uomId, Boolean activo, UsuarioPrincipal principal) {
        exigirGestor(principal);
        Map<String, Object> p = producto(productId);
        if (nombre != null && !nombre.isBlank()) {
            String n = normalizarNombre(nombre);
            validarNoDuplicado((UUID) p.get("family_id"), n, productId);
            jdbc.update("update producto set name = ? where product_id = ?", n, productId);
        }
        if (uomId != null) {
            unidad(uomId);
            jdbc.update("update producto set base_uom_id = ? where product_id = ?", uomId, productId);
        }
        if (activo != null) {
            jdbc.update("update producto set active = ? where product_id = ?", activo, productId);
        }
        return creado(productId);
    }

    private ProductoCreadoDto crear(UUID familyId, String nombre, UUID uomId, List<VarianteInput> variantes,
                                    UUID aprobadoPor, UUID solicitudId) {
        // Bloquea la familia: dos altas simultaneas no deben tomar el mismo correlativo.
        List<Map<String, Object>> fam = jdbc.queryForList("""
                select f.family_id, f.code as fam_code, t.product_type_id, t.code as tipo_code
                from familia_producto f join tipo_producto t on t.product_type_id = f.product_type_id
                where f.family_id = ? and f.active
                for update of f""", familyId);
        if (fam.isEmpty()) {
            throw ApiException.badRequest("Familia no encontrada.");
        }
        unidad(uomId);
        String n = normalizarNombre(nombre);
        validarNoDuplicado(familyId, n, null);

        String prefijo = fam.get(0).get("tipo_code") + (String) fam.get(0).get("fam_code");
        Integer ultimo = jdbc.queryForObject(
                "select max(substring(sku_base from 5)::int) from producto where sku_base ~ ?", Integer.class,
                "^" + prefijo + "[0-9]{3}$");
        int siguiente = (ultimo == null ? 0 : ultimo) + 1;
        if (siguiente > 999) {
            throw ApiException.badRequest("La familia " + prefijo + " ya no tiene correlativos disponibles.");
        }
        String sku = prefijo + String.format("%03d", siguiente);

        UUID subfamilia = subfamiliaGeneral(familyId);
        UUID productId = UUID.randomUUID();
        jdbc.update("""
                insert into producto (product_id, sku_base, product_type_id, family_id, subfamily_id, name, base_uom_id,
                  active, approved_request_id, approved_by, approved_at)
                values (?, ?, ?, ?, ?, ?, ?, true, ?, ?, now())""",
                productId, sku, fam.get(0).get("product_type_id"), familyId, subfamilia, n, uomId, solicitudId, aprobadoPor);

        List<VarianteInput> conAtributo = variantes == null ? List.of()
                : variantes.stream().filter(v -> v != null && v.valor() != null && !v.valor().isBlank()).toList();
        if (conAtributo.isEmpty()) {
            jdbc.update("""
                    insert into producto_variante (product_id, attribute_type, attribute_value, attribute_code, internal_variant_code)
                    values (?, null, null, ?, ?)""", productId, SIN_ATRIBUTO, sku + "-" + SIN_ATRIBUTO);
        } else {
            Set<String> tipos = new HashSet<>();
            for (VarianteInput v : conAtributo) {
                tipos.add(tipoAtributo(v.tipo()));
            }
            if (tipos.size() > 1) {
                throw ApiException.badRequest("Todas las variantes deben ser del mismo tipo de atributo.");
            }
            for (VarianteInput v : conAtributo) {
                insertarVariante(productId, sku, tipoAtributo(v.tipo()), v.valor());
            }
        }
        return creado(productId);
    }

    private void insertarVariante(UUID productId, String sku, String tipo, String valor) {
        if (valor == null || valor.isBlank()) {
            throw ApiException.badRequest("Indica el valor del atributo.");
        }
        String v = valor.trim().toUpperCase(Locale.ROOT);
        String codigo = v.replaceAll("[^A-Z0-9]", "");
        if (codigo.isEmpty() || codigo.length() > 20) {
            throw ApiException.badRequest("Valor de atributo invalido: \"" + valor + "\" (usa letras y numeros).");
        }
        String interno = sku + "-" + codigo;
        Integer existe = jdbc.queryForObject(
                "select count(*) from producto_variante where internal_variant_code = ?", Integer.class, interno);
        if (existe != null && existe > 0) {
            throw ApiException.badRequest("La variante " + interno + " ya existe.");
        }
        jdbc.update("""
                insert into producto_variante (product_id, attribute_type, attribute_value, attribute_code, internal_variant_code)
                values (?, ?, ?, ?, ?)""", productId, tipo, v, codigo, interno);
    }

    // ---------------------------------------------------------------- solicitudes (RF-004)

    @Transactional
    public SolicitudProductoDto crearSolicitud(CrearSolicitudRequest r, UsuarioPrincipal principal) {
        UUID empresa = jdbc.queryForList("select company_id from usuario where user_id = ?", UUID.class, principal.userId())
                .stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Tu usuario no tiene empresa asignada."));
        List<Map<String, Object>> fam = jdbc.queryForList(
                "select family_id, product_type_id from familia_producto where family_id = ? and active", r.familyId());
        if (fam.isEmpty()) {
            throw ApiException.badRequest("Familia no encontrada.");
        }
        unidad(r.uomId());
        String tipo = null;
        String valor = null;
        if (r.atributoValor() != null && !r.atributoValor().isBlank()) {
            tipo = tipoAtributo(r.atributoTipo());
            valor = r.atributoValor().trim().toUpperCase(Locale.ROOT);
        }
        String nombre = normalizarNombre(r.nombre());
        String parecido = parecido(r.familyId(), nombre);
        Integer n = jdbc.queryForObject("select count(*) from solicitud_maestro_producto", Integer.class);
        String codigo = String.format("SMP-%05d", (n == null ? 0 : n) + 1);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into solicitud_maestro_producto (product_master_request_id, request_code, request_type, company_id,
                  requested_by, proposed_product_type_id, proposed_family_id, proposed_subfamily_id, proposed_name,
                  proposed_uom_id, proposed_attribute_type, proposed_attribute_value, technical_description, justification,
                  duplicate_check_status, status)
                values (?, ?, 'ALTA_PRODUCTO', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE_APROBACION_LOGISTICA')""",
                id, codigo, empresa, principal.userId(), fam.get(0).get("product_type_id"), r.familyId(),
                subfamiliaGeneral(r.familyId()), nombre, r.uomId(), tipo, valor, blankToNull(r.descripcionTecnica()),
                r.justificacion().trim(), parecido == null ? "SIN_COINCIDENCIA" : "POSIBLE_DUPLICADO");
        return solicitud(id);
    }

    /** Logistica ve todas; el solicitante, las suyas. Pendientes primero. */
    public List<SolicitudProductoDto> listarSolicitudes(UsuarioPrincipal principal) {
        String filtro = esGestor(principal) ? "" : " where s.requested_by = ?";
        Object[] args = esGestor(principal) ? new Object[0] : new Object[]{principal.userId()};
        return jdbc.query(SELECT_SOLICITUD + filtro
                        + " order by (s.status = 'PENDIENTE_APROBACION_LOGISTICA') desc, s.requested_at desc limit 200",
                (rs, i) -> mapear(rs), args);
    }

    @Transactional
    public SolicitudProductoDto aprobarSolicitud(UUID id, AprobarSolicitudRequest r, UsuarioPrincipal principal) {
        exigirGestor(principal);
        SolicitudProductoDto s = pendiente(id);
        List<VarianteInput> variantes = s.atributoValor() == null ? List.of()
                : List.of(new VarianteInput(s.atributoTipo(), s.atributoValor()));
        ProductoCreadoDto p = crear(r.familyId(), r.nombre(), r.uomId(), variantes, principal.userId(), id);
        jdbc.update("""
                update solicitud_maestro_producto set status = 'APROBADO', reviewed_by = ?, reviewed_at = now(),
                  generated_product_id = ? where product_master_request_id = ?""", principal.userId(), p.productId(), id);
        return solicitud(id);
    }

    @Transactional
    public SolicitudProductoDto rechazarSolicitud(UUID id, String motivo, UsuarioPrincipal principal) {
        exigirGestor(principal);
        pendiente(id);
        jdbc.update("""
                update solicitud_maestro_producto set status = 'RECHAZADO', reviewed_by = ?, reviewed_at = now(),
                  review_comment = ? where product_master_request_id = ?""", principal.userId(), motivo.trim(), id);
        return solicitud(id);
    }

    // ---------------------------------------------------------------- utilidades

    private static final String SELECT_SOLICITUD = """
            select s.product_master_request_id, s.request_code, s.status, e.code as empresa, u.display_name,
              s.requested_at, s.proposed_family_id, t.code || f.code || ' ' || t.name || ' › ' || f.name as familia, s.proposed_name,
              s.proposed_uom_id, m.code as unidad, s.proposed_attribute_type, s.proposed_attribute_value,
              s.technical_description, s.justification, s.duplicate_check_status, s.review_comment, p.sku_base
            from solicitud_maestro_producto s
            join empresa e on e.company_id = s.company_id
            join usuario u on u.user_id = s.requested_by
            join familia_producto f on f.family_id = s.proposed_family_id
            join tipo_producto t on t.product_type_id = f.product_type_id
            join unidad_medida m on m.uom_id = s.proposed_uom_id
            left join producto p on p.product_id = s.generated_product_id""";

    private SolicitudProductoDto mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp en = rs.getTimestamp(6);
        String posible = "POSIBLE_DUPLICADO".equals(rs.getString(16))
                ? parecido(rs.getObject(7, UUID.class), rs.getString(9)) : null;
        return new SolicitudProductoDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), en != null ? en.toInstant() : null, rs.getObject(7, UUID.class), rs.getString(8),
                rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11), rs.getString(12), rs.getString(13),
                rs.getString(14), rs.getString(15), posible, rs.getString(17), rs.getString(18));
    }

    private SolicitudProductoDto solicitud(UUID id) {
        return jdbc.query(SELECT_SOLICITUD + " where s.product_master_request_id = ?", (rs, i) -> mapear(rs), id)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Solicitud no encontrada."));
    }

    private SolicitudProductoDto pendiente(UUID id) {
        List<String> estado = jdbc.queryForList(
                "select status from solicitud_maestro_producto where product_master_request_id = ? for update", String.class, id);
        if (estado.isEmpty()) {
            throw ApiException.notFound("Solicitud no encontrada.");
        }
        if (!"PENDIENTE_APROBACION_LOGISTICA".equals(estado.get(0))) {
            throw ApiException.badRequest("La solicitud ya fue revisada.");
        }
        return solicitud(id);
    }

    private Map<String, Object> producto(UUID productId) {
        List<Map<String, Object>> p = jdbc.queryForList(
                "select product_id, sku_base, family_id from producto where product_id = ?", productId);
        if (p.isEmpty()) {
            throw ApiException.notFound("Producto no encontrado.");
        }
        return p.get(0);
    }

    private ProductoCreadoDto creado(UUID productId) {
        Map<String, Object> p = jdbc.queryForMap("select sku_base, name from producto where product_id = ?", productId);
        List<String> variantes = jdbc.queryForList(
                "select internal_variant_code from producto_variante where product_id = ? order by internal_variant_code",
                String.class, productId);
        return new ProductoCreadoDto(productId, (String) p.get("sku_base"), (String) p.get("name"), variantes);
    }

    private void unidad(UUID uomId) {
        Integer n = jdbc.queryForObject("select count(*) from unidad_medida where uom_id = ? and active", Integer.class, uomId);
        if (n == null || n == 0) {
            throw ApiException.badRequest("Unidad de medida no encontrada.");
        }
    }

    /** Las familias nuevas pueden no tener subfamilia: se usa (o crea) la "00 GENERAL", como en el seed. */
    private UUID subfamiliaGeneral(UUID familyId) {
        List<UUID> s = jdbc.queryForList(
                "select subfamily_id from subfamilia_producto where family_id = ? and code = '00'", UUID.class, familyId);
        if (!s.isEmpty()) {
            return s.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into subfamilia_producto (subfamily_id, family_id, code, name) values (?, ?, '00', 'GENERAL')",
                id, familyId);
        return id;
    }

    private void validarNoDuplicado(UUID familyId, String nombre, UUID excepto) {
        List<String> dup = jdbc.queryForList("""
                select sku_base from producto where family_id = ? and upper(name) = ? and (?::uuid is null or product_id <> ?)""",
                String.class, familyId, nombre, excepto, excepto);
        if (!dup.isEmpty()) {
            throw ApiException.badRequest("Ya existe un producto con ese nombre en la familia (" + dup.get(0) + ").");
        }
    }

    /** Producto existente con nombre parecido en la familia (para advertir posibles duplicados). */
    private String parecido(UUID familyId, String nombre) {
        String clave = nombre.length() > 12 ? nombre.substring(0, 12) : nombre;
        List<String> p = jdbc.queryForList("""
                select sku_base || ' ' || name from producto
                where family_id = ? and active and (upper(name) = ? or upper(name) like ?) order by sku_base limit 1""",
                String.class, familyId, nombre, clave + "%");
        return p.isEmpty() ? null : p.get(0);
    }

    private static String tipoAtributo(String tipo) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase(Locale.ROOT);
        if (!TIPOS_ATRIBUTO.contains(t)) {
            throw ApiException.badRequest("Tipo de atributo invalido (TALLA, COLOR, MODELO u OTRO).");
        }
        return t;
    }

    private static String normalizarNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (n.length() < 3) {
            throw ApiException.badRequest("El nombre del producto es muy corto.");
        }
        return n;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
