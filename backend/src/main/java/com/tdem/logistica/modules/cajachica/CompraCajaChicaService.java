package com.tdem.logistica.modules.cajachica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.cajachica.dto.CompraCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.ConsolidadoCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.RegistrarCompraCajaChicaRequest;
import com.tdem.logistica.modules.compras.CodigoCompra;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Caja chica como modulo propio (DEC-L092): el solicitante registra lo que compro
 * con la caja chica de su proyecto, sin requerimiento ni revision, con la factura
 * obligatoria (archivo o enlace). Logistica, Gerencia y el aprobador del CECO
 * (o su suplente) ven el consolidado; quien registro o Logistica puede anular con
 * motivo. Montos en soles.
 */
@Service
@Transactional(readOnly = true)
public class CompraCajaChicaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** DEC-L096: solo boleta o factura (los registros anteriores pueden tener otros tipos). */
    private static final Set<String> TIPOS = Set.of("FACTURA", "BOLETA");
    private static final Set<String> FORMATOS = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final Pattern RUC = Pattern.compile("\\d{11}");
    private static final Pattern PERIODO = Pattern.compile("\\d{4}-\\d{2}");
    private static final int MAX_DETALLE = 500;

    private static final String SELECT = """
            select c.purchase_id, c.purchase_code, e.code as empresa, ce.code as ceco_code, ce.name as ceco_nombre,
                   u.display_name as solicitante, c.purchase_user_id, c.purchase_date, c.supplier_ruc, c.supplier_name,
                   c.document_type, c.document_series, c.document_number, c.total, d.file_url as archivo,
                   c.evidence_url, c.notes, c.status, c.annulment_reason, c.created_at
              from caja_chica_compra c
              join empresa e on e.company_id = c.company_id
              join ceco ce on ce.ceco_id = c.ceco_id
              join usuario u on u.user_id = c.purchase_user_id
              left join documento d on d.document_id = c.evidence_document_id
            """;

    /** Lo que el usuario puede ver en el consolidado: todo, o los CECOs que aprueba o suple. */
    private static final String AMBITO = """
            (:todos or c.ceco_id in (select ceco_id from ceco
                                      where approver_user_id = :yo or backup_approver_user_id = :yo))""";

    private final NamedParameterJdbcTemplate jdbc;
    private final ComprasService compras;
    private final Auditoria auditoria;
    private final com.tdem.logistica.repository.DocumentoRepository documentoRepository;

    public CompraCajaChicaService(NamedParameterJdbcTemplate jdbc, ComprasService compras, Auditoria auditoria,
                                  com.tdem.logistica.repository.DocumentoRepository documentoRepository) {
        this.documentoRepository = documentoRepository;
        this.jdbc = jdbc;
        this.compras = compras;
        this.auditoria = auditoria;
    }

    // ---------------------------------------------------------------- registro

    @Transactional
    public CompraCajaChicaDto registrar(RegistrarCompraCajaChicaRequest r, MultipartFile factura, UsuarioPrincipal principal) {
        boolean gestor = principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
        Map<String, Object> ceco = jdbc.query("""
                        select c.ceco_id, c.company_id, e.code, c.active,
                               exists (select 1 from usuario_ceco uc where uc.ceco_id = c.ceco_id and uc.user_id = :yo) as mio
                          from ceco c join empresa e on e.company_id = c.company_id where c.ceco_id = :ceco""",
                new MapSqlParameterSource("ceco", r.cecoId()).addValue("yo", principal.userId()),
                rs -> rs.next() ? Map.of("company", rs.getObject(2), "empresa", rs.getString(3),
                        "activo", rs.getBoolean(4), "mio", rs.getBoolean(5)) : null);
        if (ceco == null || !(Boolean) ceco.get("activo")) {
            throw ApiException.badRequest("CECO no encontrado.");
        }
        if (!gestor && !(Boolean) ceco.get("mio")) {
            throw ApiException.forbidden("Solo registras compras de caja chica de tus CECOs asignados.");
        }
        LocalDate hoy = LocalDate.now(LIMA);
        if (r.fecha().isAfter(hoy)) {
            throw ApiException.badRequest("La fecha de compra no puede ser posterior a hoy.");
        }
        String tipo = r.tipoComprobante().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) {
            throw ApiException.badRequest("El comprobante es una boleta o una factura.");
        }
        String ruc = limpio(r.proveedorRuc());
        if (ruc != null && !RUC.matcher(ruc).matches()) {
            throw ApiException.badRequest("El RUC tiene 11 dígitos.");
        }
        if ("FACTURA".equals(tipo) && ruc == null) {
            throw ApiException.badRequest("Una factura lleva el RUC del proveedor.");
        }
        String url = limpio(r.facturaUrl());
        boolean conArchivo = factura != null && !factura.isEmpty();
        if (!conArchivo && url == null) {
            throw ApiException.badRequest("Adjunta la factura (foto o PDF) o indica el enlace donde está.");
        }
        if (url != null && !url.matches("(?i)https?://\\S+")) {
            throw ApiException.badRequest("El enlace de la factura debe empezar con http:// o https://.");
        }
        if (conArchivo && !FORMATOS.contains(factura.getContentType())) {
            throw ApiException.badRequest("La factura va en PDF, JPG, PNG o WEBP.");
        }

        List<Object[]> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Map<UUID, String[]> productos = productos(r.lineas());
        for (RegistrarCompraCajaChicaRequest.Linea l : r.lineas()) {
            String[] producto = l.variantId() != null ? productos.get(l.variantId()) : null;
            if (l.variantId() != null && producto == null) {
                throw ApiException.badRequest("Uno de los productos no existe o está inactivo.");
            }
            String descripcion = producto != null ? producto[2] : limpio(l.descripcion());
            if (descripcion == null) {
                throw ApiException.badRequest("Describe cada línea de lo comprado.");
            }
            BigDecimal linea = l.cantidad().multiply(l.precioUnitario()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(linea);
            lineas.add(new Object[] {producto != null ? UUID.fromString(producto[0]) : null, l.variantId(),
                    recortar(descripcion, 300), l.cantidad(), l.precioUnitario(), linea});
        }
        if (total.signum() <= 0) {
            throw ApiException.badRequest("El total de la compra debe ser mayor que cero.");
        }

        UUID documento = conArchivo
                ? compras.guardarDocumento(factura, "COMPROBANTE_CAJA_CHICA", principal.userId()).documentId() : null;
        // El documento se guarda por JPA y la compra por JDBC: se escribe antes para que exista la FK.
        documentoRepository.flush();
        UUID id = UUID.randomUUID();
        String codigo = CodigoCompra.generar("CCH", (String) ceco.get("empresa"));
        try {
            jdbc.update("""
                    insert into caja_chica_compra (purchase_id, purchase_code, company_id, ceco_id, purchase_user_id,
                      purchase_date, supplier_ruc, supplier_name, document_type, document_series, document_number,
                      total, evidence_document_id, evidence_url, notes)
                    values (:id, :codigo, :company, :ceco, :yo, :fecha, :ruc, :nombre, :tipo, :serie, :numero,
                            :total, :documento, :url, :nota)""",
                    new MapSqlParameterSource()
                            .addValue("id", id).addValue("codigo", codigo).addValue("company", ceco.get("company"))
                            .addValue("ceco", r.cecoId()).addValue("yo", principal.userId())
                            .addValue("fecha", r.fecha()).addValue("ruc", ruc, Types.VARCHAR)
                            .addValue("nombre", r.proveedorNombre().trim())
                            .addValue("tipo", tipo).addValue("serie", limpioMayus(r.serie()), Types.VARCHAR)
                            .addValue("numero", r.numero().trim().toUpperCase()).addValue("total", total)
                            .addValue("documento", documento, Types.OTHER).addValue("url", url, Types.VARCHAR)
                            .addValue("nota", limpio(r.nota()), Types.VARCHAR));
        } catch (DuplicateKeyException e) {
            throw ApiException.badRequest("Ese comprobante ya está registrado en caja chica.");
        }
        List<MapSqlParameterSource> filas = new ArrayList<>();
        for (Object[] l : lineas) {
            filas.add(new MapSqlParameterSource().addValue("compra", id).addValue("producto", l[0], Types.OTHER)
                    .addValue("variante", l[1], Types.OTHER).addValue("descripcion", l[2])
                    .addValue("cantidad", l[3]).addValue("precio", l[4]).addValue("total", l[5]));
        }
        jdbc.batchUpdate("""
                insert into caja_chica_compra_linea (purchase_id, product_id, variant_id, description, quantity,
                  unit_price, line_total)
                values (:compra, :producto, :variante, :descripcion, :cantidad, :precio, :total)""",
                filas.toArray(MapSqlParameterSource[]::new));
        auditoria.registrar("CAJA_CHICA_COMPRA", id, "REGISTRAR", null,
                Map.of("codigo", codigo, "total", total, "comprobante", tipo + " " + r.numero().trim()), principal.userId());
        return obtener(id);
    }

    /** [productId, sku, nombre] de las variantes activas elegidas. */
    private Map<UUID, String[]> productos(List<RegistrarCompraCajaChicaRequest.Linea> lineas) {
        List<UUID> ids = lineas.stream().map(RegistrarCompraCajaChicaRequest.Linea::variantId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String[]> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("""
                select v.variant_id, p.product_id, v.internal_variant_code,
                       p.name || coalesce(' (' || nullif(v.attribute_value, '') || ')', '')
                  from producto_variante v join producto p on p.product_id = v.product_id
                 where v.variant_id in (:ids) and v.active and p.active""",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    out.put(rs.getObject(1, UUID.class),
                            new String[] {rs.getString(2), rs.getString(3), rs.getString(4)});
                });
        return out;
    }

    // ---------------------------------------------------------------- anulacion

    /** Quien registro o Logistica/Admin; con motivo, auditado. */
    @Transactional
    public CompraCajaChicaDto anular(UUID id, String motivo, UsuarioPrincipal principal) {
        String razon = limpio(motivo);
        if (razon == null || razon.length() < 5) {
            throw ApiException.badRequest("Indica el motivo de la anulación.");
        }
        CompraCajaChicaDto c = obtener(id);
        boolean gestor = principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
        if (!gestor && !principal.userId().equals(c.solicitanteId())) {
            throw ApiException.forbidden("Solo quien la registró o Logística la anulan.");
        }
        if ("ANULADA".equals(c.estado())) {
            throw ApiException.badRequest("La compra ya está anulada.");
        }
        jdbc.update("""
                update caja_chica_compra set status = 'ANULADA', annulled_at = now(), annulled_by = :yo,
                       annulment_reason = :motivo where purchase_id = :id""",
                new MapSqlParameterSource("id", id).addValue("yo", principal.userId()).addValue("motivo", razon));
        auditoria.registrar("CAJA_CHICA_COMPRA", id, "ANULAR", Map.of("estado", "REGISTRADA"),
                Map.of("estado", "ANULADA", "motivo", razon), principal.userId());
        return obtener(id);
    }

    // ---------------------------------------------------------------- lectura

    /** Lo que registro el usuario (las ultimas 200). */
    public List<CompraCajaChicaDto> mias(UsuarioPrincipal principal) {
        return leer(SELECT + " where c.purchase_user_id = :yo order by c.purchase_date desc, c.created_at desc limit 200",
                new MapSqlParameterSource("yo", principal.userId()));
    }

    private CompraCajaChicaDto obtener(UUID id) {
        return leer(SELECT + " where c.purchase_id = :id", new MapSqlParameterSource("id", id)).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("Compra de caja chica no encontrada."));
    }

    /**
     * Consolidado en PEN de {@code desde} a {@code hasta} (AAAA-MM, por defecto el mes
     * actual), filtrable por empresa y CECO. Logistica, Gerencia y Admin ven todo; el
     * aprobador, los CECOs que aprueba o suple.
     */
    public ConsolidadoCajaChicaDto consolidado(String desde, String hasta, String empresa, UUID cecoId,
                                               UsuarioPrincipal principal) {
        MapSqlParameterSource p = filtros(desde, hasta, empresa, cecoId, principal);
        String base = " from caja_chica_compra c join ceco ce on ce.ceco_id = c.ceco_id"
                + " join empresa e on e.company_id = c.company_id join usuario u on u.user_id = c.purchase_user_id"
                + " where " + AMBITO + FILTRO;
        Object[] totales = jdbc.queryForObject("select coalesce(sum(c.total) filter (where c.status = 'REGISTRADA'), 0),"
                + " count(*) filter (where c.status = 'REGISTRADA'), count(*) filter (where c.status = 'ANULADA')" + base, p,
                (rs, i) -> new Object[] {rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3)});
        List<ConsolidadoCajaChicaDto.Monto> porCeco = montos("select ce.code, ce.name || ' · ' || e.code, sum(c.total), count(*)"
                + base + " and c.status = 'REGISTRADA' group by ce.code, ce.name, e.code order by 3 desc", p);
        List<ConsolidadoCajaChicaDto.Monto> porMes = montos("select to_char(c.purchase_date, 'YYYY-MM'), null, sum(c.total), count(*)"
                + base + " and c.status = 'REGISTRADA' group by 1 order by 1", p);
        List<ConsolidadoCajaChicaDto.Monto> porSolicitante = montos("select u.display_name, null, sum(c.total), count(*)"
                + base + " and c.status = 'REGISTRADA' group by u.display_name order by 3 desc", p);
        List<CompraCajaChicaDto> detalle = leer(SELECT + " where " + AMBITO + FILTRO
                + " order by c.purchase_date desc, c.created_at desc limit " + MAX_DETALLE, p);
        YearMonth ini = YearMonth.parse((String) p.getValue("desde"));
        YearMonth fin = YearMonth.parse((String) p.getValue("hasta"));
        List<String> meses = new ArrayList<>();
        for (YearMonth m = ini; !m.isAfter(fin); m = m.plusMonths(1)) {
            meses.add(m.toString());
        }
        return new ConsolidadoCajaChicaDto((BigDecimal) totales[0], (Long) totales[1], (Long) totales[2], meses,
                porCeco, porMes, porSolicitante, detalle);
    }

    private List<ConsolidadoCajaChicaDto.Monto> montos(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new ConsolidadoCajaChicaDto.Monto(rs.getString(1), rs.getString(2),
                rs.getBigDecimal(3), rs.getLong(4)));
    }

    /** Todas las lineas del filtro para el Excel (sin tope). */
    public List<CompraCajaChicaDto> paraExcel(String desde, String hasta, String empresa, UUID cecoId,
                                              UsuarioPrincipal principal) {
        return leer(SELECT + " where " + AMBITO + FILTRO + " order by c.purchase_date, c.purchase_code",
                filtros(desde, hasta, empresa, cecoId, principal));
    }

    private static final String FILTRO = """
             and c.purchase_date >= to_date(:desde || '-01', 'YYYY-MM-DD')
             and c.purchase_date < to_date(:hasta || '-01', 'YYYY-MM-DD') + interval '1 month'
             and (cast(:empresa as text) is null or c.company_id = (select company_id from empresa where code = cast(:empresa as text)))
             and (cast(:ceco as uuid) is null or c.ceco_id = cast(:ceco as uuid))""";

    private static MapSqlParameterSource filtros(String desde, String hasta, String empresa, UUID cecoId,
                                                 UsuarioPrincipal principal) {
        boolean todos = principal.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN, Roles.GERENCIA);
        if (!todos && !principal.tieneAlgunRol(Roles.APROBADOR)) {
            throw ApiException.forbidden("No tienes permiso para ver el consolidado de caja chica.");
        }
        String mes = YearMonth.now(LIMA).toString();
        String d = desde == null || desde.isBlank() ? mes : desde.trim();
        String h = hasta == null || hasta.isBlank() ? d : hasta.trim();
        if (!PERIODO.matcher(d).matches() || !PERIODO.matcher(h).matches() || d.compareTo(h) > 0) {
            throw ApiException.badRequest("Periodo inválido (AAAA-MM, desde no posterior a hasta).");
        }
        if (YearMonth.parse(d).plusMonths(24).isBefore(YearMonth.parse(h))) {
            throw ApiException.badRequest("El periodo admite hasta 24 meses.");
        }
        String e = empresa == null || empresa.isBlank() ? null : empresa.trim().toUpperCase();
        return new MapSqlParameterSource()
                .addValue("todos", todos).addValue("yo", principal.userId())
                .addValue("desde", d).addValue("hasta", h)
                .addValue("empresa", e, Types.VARCHAR).addValue("ceco", cecoId, Types.OTHER);
    }

    private List<CompraCajaChicaDto> leer(String sql, MapSqlParameterSource p) {
        List<Object[]> filas = jdbc.query(sql, p, (rs, i) -> new Object[] {
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getObject(7, UUID.class), rs.getObject(8, LocalDate.class), rs.getString(9),
                rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getBigDecimal(14),
                rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), rs.getString(19),
                rs.getTimestamp(20).toInstant()});
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CompraCajaChicaDto.Linea>> lineas = new HashMap<>();
        jdbc.query("""
                select l.purchase_id, v.internal_variant_code, l.description, l.quantity, l.unit_price, l.line_total
                  from caja_chica_compra_linea l left join producto_variante v on v.variant_id = l.variant_id
                 where l.purchase_id in (:ids) order by l.purchase_id, l.description""",
                new MapSqlParameterSource("ids", filas.stream().map(f -> f[0]).toList()),
                rs -> {
                    lineas.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new CompraCajaChicaDto.Linea(rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                                    rs.getBigDecimal(5), rs.getBigDecimal(6)));
                });
        return filas.stream().map(f -> new CompraCajaChicaDto((UUID) f[0], (String) f[1], (String) f[2],
                (String) f[3], (String) f[4], (String) f[5], (UUID) f[6], (LocalDate) f[7], (String) f[8],
                (String) f[9], (String) f[10], (String) f[11], (String) f[12], (BigDecimal) f[13],
                f[14] != null && ((String) f[14]).startsWith("http") ? (String) f[14] : null, (String) f[15],
                (String) f[16], (String) f[17], (String) f[18], (java.time.Instant) f[19],
                lineas.getOrDefault((UUID) f[0], List.of()))).toList();
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limpioMayus(String s) {
        String l = limpio(s);
        return l == null ? null : l.toUpperCase();
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
