package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.presupuestos.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static com.tdem.logistica.common.AppConstants.EstadoPresupuesto.*;

/**
 * Presupuestos semestrales por CECO (Lote 14, DEC-L036; presupuestos-*.js de la V38.7).
 * <ul>
 *   <li>Ambito de un usuario: sus CECOs (usuario_ceco) y los CECOs donde es el
 *       aprobador asignado. LOGISTICA y ADMIN ven todo, sin editar ni decidir.</li>
 *   <li>Periodos: los habilita y cierra PYC en su ambito. Cerrado = solo consulta.</li>
 *   <li>Redactan (crear, programar, emitir, modificar): PYC, GERENCIA y el
 *       APROBADOR del CECO en su ambito; el SOLICITANTE solo los suyos.</li>
 *   <li>Deciden (aprobar, observar, anular): el aprobador copiado al emitir y la
 *       GERENCIA del CECO, nunca el solicitante del presupuesto.</li>
 *   <li>Aprobar congela una version: cantidades, precio de venta vigente y el
 *       ultimo precio de compra en PEN. Una revision (MODIFICADO) sigue rigiendo
 *       con su ultima version aprobada.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PresupuestoService {

    private static final Set<String> EDITABLES = Set.of(BORRADOR, OBSERVADO, MODIFICADO);
    private static final Set<String> ANULABLES = Set.of(EMITIDO, APROBADO, OBSERVADO, MODIFICADO);

    /** CECOs del ambito del usuario; recibe dos veces el userId. */
    private static final String AMBITO = """
            select ceco_id from usuario_ceco where user_id = ?
            union select ceco_id from ceco where approver_user_id = ?""";

    /** Precio de la partida: congelado si esta APROBADO, si no el de venta vigente. */
    private static final String PRECIO = "(case when b.status = 'APROBADO' then vl.sale_price else pv.sale_price end)";

    private static final String JOIN_PRECIOS = """
            left join presupuesto_version_linea vl
              on vl.budget_version_id = b.current_version_id and vl.product_id = l.product_id
            left join precio_venta_producto pv
              on pv.company_id = c.company_id and pv.product_id = l.product_id and pv.active""";

    /** Columnas de {@link Ctx}; recibe dos veces el userId (ambito del CECO). */
    private static final String CTX_COLUMNAS = """
            b.budget_id, b.budget_code, b.budget_period_id, pp.ceco_id, c.company_id, c.approver_user_id,
            pp.closed, b.status, b.requester_user_id, b.approver_user_id, b.current_version_id,
            (coalesce(c.approver_user_id = ?, false) or exists (select 1 from usuario_ceco uc
                                               where uc.user_id = ? and uc.ceco_id = pp.ceco_id)) as en_ambito""";

    private static final String CTX_FROM = """
            from presupuesto b
            join periodo_presupuestal pp on pp.budget_period_id = b.budget_period_id
            join ceco c on c.ceco_id = pp.ceco_id""";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final Auditoria auditoria;
    private final ControlPresupuestalService control;
    private final BorradoresPresupuestoService borradores;

    public PresupuestoService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, Auditoria auditoria,
                              ControlPresupuestalService control, BorradoresPresupuestoService borradores) {
        this.jdbc = jdbc;
        this.named = named;
        this.auditoria = auditoria;
        this.control = control;
        this.borradores = borradores;
    }

    /** Lo necesario para autorizar una accion sobre un presupuesto. */
    private record Ctx(UUID id, String codigo, UUID periodId, UUID cecoId, UUID companyId, UUID cecoApprover,
                       boolean cerrado, String estado, UUID requesterId, UUID approverId, UUID currentVersionId,
                       boolean enAmbito) {
    }

    // ---------------------------------------------------------------- periodos

    public PeriodosDto listarPeriodos(UsuarioPrincipal principal) {
        exigirAcceso(principal);
        UUID yo = principal.userId();
        String sql = """
                select pp.budget_period_id, pp.ceco_id, e.code, c.code, c.name, pp.year, pp.semester, pp.closed,
                       b.budget_id, b.budget_code, pp.mandatory_control
                from periodo_presupuestal pp
                join ceco c on c.ceco_id = pp.ceco_id
                join empresa e on e.company_id = c.company_id
                left join presupuesto b on b.budget_period_id = pp.budget_period_id and b.status <> 'ANULADO'
                """ + (veTodo(principal) ? "" : "where pp.ceco_id in (" + AMBITO + ")\n")
                + "order by pp.year desc, pp.semester desc, e.code, c.code";
        Object[] args = veTodo(principal) ? new Object[0] : new Object[]{yo, yo};
        List<PeriodoDto> periodos = jdbc.query(sql, (rs, i) -> mapearPeriodo(rs), args);

        List<CecoOpcionDto> cecos = esPyc(principal)
                ? jdbc.query("""
                        select c.ceco_id, e.code, c.code, c.name from ceco c join empresa e on e.company_id = c.company_id
                        where c.active and c.ceco_id in (""" + AMBITO + ") order by e.code, c.code",
                (rs, i) -> new CecoOpcionDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4)), yo, yo)
                : List.of();
        return new PeriodosDto(periodos, cecos, esRedactor(principal));
    }

    @Transactional
    public PeriodoDto crearPeriodo(CrearPeriodoRequest r, UsuarioPrincipal principal) {
        exigirPyc(principal);
        List<Boolean> ceco = jdbc.queryForList("select active from ceco where ceco_id = ?", Boolean.class, r.cecoId());
        if (ceco.isEmpty() || !ceco.get(0)) {
            throw ApiException.badRequest("Selecciona un CECO activo.");
        }
        exigirCecoEnAmbito(r.cecoId(), principal);
        Integer existe = jdbc.queryForObject("""
                select count(*) from periodo_presupuestal where ceco_id = ? and year = ? and semester = ?""",
                Integer.class, r.cecoId(), r.anio(), r.semestre());
        if (existe != null && existe > 0) {
            throw ApiException.badRequest("El periodo " + r.anio() + "-S" + r.semestre() + " ya existe para este CECO.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into periodo_presupuestal (budget_period_id, ceco_id, year, semester, created_by)
                values (?, ?, ?, ?, ?)""", id, r.cecoId(), r.anio(), r.semestre(), principal.userId());
        auditoria.registrar("PERIODO_PRESUPUESTAL", id, "CREAR", null,
                Map.of("ceco", r.cecoId(), "periodo", r.anio() + "-S" + r.semestre()), principal.userId());
        return periodo(id);
    }

    @Transactional
    public PeriodoDto cerrarPeriodo(UUID periodId, boolean cerrado, UsuarioPrincipal principal) {
        exigirPyc(principal);
        List<Map<String, Object>> p = jdbc.queryForList(
                "select ceco_id, closed from periodo_presupuestal where budget_period_id = ? for update", periodId);
        if (p.isEmpty()) {
            throw ApiException.notFound("Periodo no encontrado.");
        }
        exigirCecoEnAmbito((UUID) p.get(0).get("ceco_id"), principal);
        if ((Boolean) p.get(0).get("closed") != cerrado) {
            jdbc.update("""
                    update periodo_presupuestal set closed = ?, updated_by = ?, updated_at = now()
                    where budget_period_id = ?""", cerrado, principal.userId(), periodId);
            auditoria.registrar("PERIODO_PRESUPUESTAL", periodId, cerrado ? "CERRAR" : "REABRIR",
                    Map.of("cerrado", !cerrado), Map.of("cerrado", cerrado), principal.userId());
        }
        return periodo(periodId);
    }

    /** PYC activa o desactiva el control presupuestal de los REQ del periodo (DEC-L038). */
    @Transactional
    public PeriodoDto controlPeriodo(UUID periodId, boolean obligatorio, UsuarioPrincipal principal) {
        exigirPyc(principal);
        List<Map<String, Object>> p = jdbc.queryForList(
                "select ceco_id, mandatory_control from periodo_presupuestal where budget_period_id = ? for update",
                periodId);
        if (p.isEmpty()) {
            throw ApiException.notFound("Periodo no encontrado.");
        }
        exigirCecoEnAmbito((UUID) p.get(0).get("ceco_id"), principal);
        if ((Boolean) p.get(0).get("mandatory_control") != obligatorio) {
            jdbc.update("""
                    update periodo_presupuestal set mandatory_control = ?, updated_by = ?, updated_at = now()
                    where budget_period_id = ?""", obligatorio, principal.userId(), periodId);
            auditoria.registrar("PERIODO_PRESUPUESTAL", periodId, obligatorio ? "ACTIVAR_CONTROL" : "DESACTIVAR_CONTROL",
                    Map.of("control", !obligatorio), Map.of("control", obligatorio), principal.userId());
        }
        return periodo(periodId);
    }

    private PeriodoDto periodo(UUID periodId) {
        return jdbc.query("""
                select pp.budget_period_id, pp.ceco_id, e.code, c.code, c.name, pp.year, pp.semester, pp.closed,
                       b.budget_id, b.budget_code, pp.mandatory_control
                from periodo_presupuestal pp
                join ceco c on c.ceco_id = pp.ceco_id
                join empresa e on e.company_id = c.company_id
                left join presupuesto b on b.budget_period_id = pp.budget_period_id and b.status <> 'ANULADO'
                where pp.budget_period_id = ?""", (rs, i) -> mapearPeriodo(rs), periodId).getFirst();
    }

    private static PeriodoDto mapearPeriodo(ResultSet rs) throws SQLException {
        return new PeriodoDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8),
                rs.getObject(9, UUID.class), rs.getString(10), rs.getBoolean(11));
    }

    // ---------------------------------------------------------------- listado y detalle

    public List<PresupuestoResumenDto> listar(Integer anio, Integer semestre, String estado, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        UUID yo = principal.userId();
        StringBuilder sql = new StringBuilder("select " + CTX_COLUMNAS + """
                , e.code, c.code, c.name, pp.year, pp.semester, us.display_name, ua.display_name,
                  t.total, coalesce(t.sin_precio, 0), b.updated_at
                """ + CTX_FROM + """

                join empresa e on e.company_id = c.company_id
                join usuario us on us.user_id = b.requester_user_id
                left join usuario ua on ua.user_id = b.approver_user_id
                left join lateral (
                  select sum((l.qty_m1 + l.qty_m2 + l.qty_m3 + l.qty_m4 + l.qty_m5 + l.qty_m6) * coalesce(""" + PRECIO + """
                , 0)) as total,
                         count(*) filter (where """ + PRECIO + """
                 is null) as sin_precio
                  from presupuesto_linea l
                """ + JOIN_PRECIOS + """

                  where l.budget_id = b.budget_id) t on true
                where true""");
        List<Object> args = new ArrayList<>(List.of(yo, yo));
        if (!veTodo(principal)) {
            sql.append(" and (pp.ceco_id in (").append(AMBITO).append(") or b.approver_user_id = ? or b.requester_user_id = ?)");
            args.addAll(List.of(yo, yo, yo, yo));
        }
        if (anio != null) {
            sql.append(" and pp.year = ?");
            args.add(anio);
        }
        if (semestre != null) {
            sql.append(" and pp.semester = ?");
            args.add(semestre);
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" and b.status = ?");
            args.add(estado);
        }
        sql.append(" order by pp.year desc, pp.semester desc, b.budget_code desc");
        List<PresupuestoResumenDto> filas = jdbc.query(sql.toString(), (rs, i) -> {
            Ctx c = mapearCtx(rs);
            Timestamp actualizado = rs.getTimestamp(22);
            return new PresupuestoResumenDto(c.id(), c.codigo(), rs.getString(13), rs.getString(14), rs.getString(15),
                    rs.getInt(16), rs.getInt(17), c.estado(), rs.getString(18), rs.getString(19),
                    rs.getBigDecimal(20), rs.getInt(21), EMITIDO.equals(c.estado()) && esDecisor(c, principal),
                    actualizado != null ? actualizado.toInstant() : null);
        }, args.toArray());
        // Lo que me toca decidir va primero.
        return filas.stream().sorted(Comparator.comparing((PresupuestoResumenDto f) -> !f.porDecidir())).toList();
    }

    public PresupuestoDetalleDto obtener(UUID id, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        UUID yo = principal.userId();
        List<Object[]> cab = jdbc.query("select " + CTX_COLUMNAS + """
                , e.code, c.code, c.name, pp.year, pp.semester, us.display_name, ua.display_name,
                  b.observations, b.decision_reason, b.issued_at, v.version_no, v.approved_at, uv.display_name,
                  pp.mandatory_control
                """ + CTX_FROM + """

                join empresa e on e.company_id = c.company_id
                join usuario us on us.user_id = b.requester_user_id
                left join usuario ua on ua.user_id = b.approver_user_id
                left join presupuesto_version v on v.budget_version_id = b.current_version_id
                left join usuario uv on uv.user_id = v.approved_by
                where b.budget_id = ?""", (rs, i) -> new Object[]{mapearCtx(rs), rs.getString(13), rs.getString(14),
                rs.getString(15), rs.getInt(16), rs.getInt(17), rs.getString(18), rs.getString(19), rs.getString(20),
                rs.getString(21), instante(rs, 22), (Integer) rs.getObject(23), instante(rs, 24), rs.getString(25),
                rs.getBoolean(26)},
                yo, yo, id);
        if (cab.isEmpty() || !puedeVer((Ctx) cab.get(0)[0], principal)) {
            throw ApiException.notFound("Presupuesto no disponible.");
        }
        Object[] h = cab.get(0);
        Ctx c = (Ctx) h[0];
        List<LineaPresupuestoDto> lineas = lineas(id);
        BigDecimal total = lineas.stream().map(LineaPresupuestoDto::importe).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int sinPrecio = (int) lineas.stream().filter(l -> l.precio() == null).count();
        List<HistorialPresupuestoDto> historial = jdbc.query("""
                select lc.accion, u.display_name, lc.created_at, lc.despues ->> 'motivo'
                from log_cambios lc left join usuario u on u.user_id = lc.usuario_id
                where lc.entidad = 'PRESUPUESTO' and lc.entidad_id = ? and lc.accion <> 'PROGRAMACION'
                order by lc.created_at""",
                (rs, i) -> new HistorialPresupuestoDto(rs.getString(1), rs.getString(2), instante(rs, 3),
                        rs.getString(4)), id);
        List<RequerimientoGeneradoDto> requerimientos = jdbc.query("""
                select r.request_id, r.request_code, r.budget_month, r.status,
                       (select count(*) from requerimiento_item ri where ri.request_id = r.request_id)
                from requerimiento r where r.budget_id = ? order by r.budget_month""",
                (rs, i) -> new RequerimientoGeneradoDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3),
                        rs.getString(4), rs.getInt(5)), id);
        return new PresupuestoDetalleDto(c.id(), c.codigo(), c.periodId(), (String) h[1], (String) h[2], (String) h[3],
                (Integer) h[4], (Integer) h[5], c.cerrado(), c.estado(), c.requesterId(), (String) h[6],
                (String) h[7], (String) h[8], (String) h[9], (Instant) h[10], (Integer) h[11], (Instant) h[12],
                (String) h[13], lineas, lineas.isEmpty() ? null : total, sinPrecio,
                permisos(c, principal, lineas.size()), historial, (Boolean) h[14],
                principal.tieneAlgunRol(Roles.PYC, Roles.GERENCIA, Roles.LOGISTICA, Roles.ADMIN)
                        ? control.excepciones(c.periodId()) : List.of(),
                requerimientos);
    }

    private List<LineaPresupuestoDto> lineas(UUID budgetId) {
        return jdbc.query("""
                select l.product_id, p.sku_base, p.name, f.name, u.code,
                       l.qty_m1, l.qty_m2, l.qty_m3, l.qty_m4, l.qty_m5, l.qty_m6,
                       """ + PRECIO + """
                , (b.status = 'APROBADO' and vl.sale_price is not null)
                from presupuesto_linea l
                join presupuesto b on b.budget_id = l.budget_id
                join periodo_presupuestal pp on pp.budget_period_id = b.budget_period_id
                join ceco c on c.ceco_id = pp.ceco_id
                join producto p on p.product_id = l.product_id
                join familia_producto f on f.family_id = p.family_id
                join unidad_medida u on u.uom_id = p.base_uom_id
                """ + JOIN_PRECIOS + """

                where l.budget_id = ?
                order by f.name, p.sku_base""", (rs, i) -> {
            List<BigDecimal> meses = new ArrayList<>(6);
            BigDecimal cantidad = BigDecimal.ZERO;
            for (int m = 6; m <= 11; m++) {
                BigDecimal q = rs.getBigDecimal(m);
                meses.add(q);
                cantidad = cantidad.add(q);
            }
            BigDecimal precio = rs.getBigDecimal(12);
            return new LineaPresupuestoDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), meses, cantidad, precio,
                    precio == null ? null : precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP), rs.getBoolean(13));
        }, budgetId);
    }

    /** SKU base activos con su precio de venta vigente en la empresa del presupuesto. */
    public List<ProductoPresupuestoDto> catalogo(UUID id, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, false);
        return jdbc.query("""
                select p.product_id, p.sku_base, p.name, f.name, u.code, pv.sale_price
                from producto p
                join familia_producto f on f.family_id = p.family_id
                join unidad_medida u on u.uom_id = p.base_uom_id
                left join precio_venta_producto pv on pv.company_id = ? and pv.product_id = p.product_id and pv.active
                where p.active
                order by p.sku_base""",
                (rs, i) -> new ProductoPresupuestoDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getBigDecimal(6)), c.companyId());
    }

    // ---------------------------------------------------------------- redaccion

    @Transactional
    public PresupuestoDetalleDto crear(CrearPresupuestoRequest r, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        if (!esRedactor(principal)) {
            throw ApiException.forbidden("Sin permiso para crear presupuestos.");
        }
        UUID yo = principal.userId();
        // Bloquea el periodo: dos altas simultaneas no deben duplicar el presupuesto del semestre.
        List<Map<String, Object>> p = jdbc.queryForList("""
                select pp.ceco_id, c.approver_user_id, pp.closed,
                       (coalesce(c.approver_user_id = ?, false) or exists (select 1 from usuario_ceco uc
                                                          where uc.user_id = ? and uc.ceco_id = pp.ceco_id)) as en_ambito
                from periodo_presupuestal pp join ceco c on c.ceco_id = pp.ceco_id
                where pp.budget_period_id = ? for update of pp""", yo, yo, r.periodId());
        if (p.isEmpty()) {
            throw ApiException.notFound("Periodo no encontrado.");
        }
        Map<String, Object> periodo = p.get(0);
        if ((Boolean) periodo.get("closed")) {
            throw ApiException.badRequest("El periodo esta cerrado: solo consulta.");
        }
        boolean redacta = (Boolean) periodo.get("en_ambito") && (principal.tieneAlgunRol(Roles.PYC, Roles.GERENCIA,
                Roles.SOLICITANTE) || (principal.tieneAlgunRol(Roles.APROBADOR) && yo.equals(periodo.get("approver_user_id"))));
        if (!redacta) {
            throw ApiException.forbidden("El CECO no esta en tu ambito.");
        }
        Integer activos = jdbc.queryForObject(
                "select count(*) from presupuesto where budget_period_id = ? and status <> 'ANULADO'",
                Integer.class, r.periodId());
        if (activos != null && activos > 0) {
            throw ApiException.badRequest("Ya existe un presupuesto para este CECO y semestre.");
        }
        Integer ultimo = jdbc.queryForObject(
                "select coalesce(max(cast(substring(budget_code from 5) as integer)), 0) from presupuesto", Integer.class);
        String codigo = String.format("PRE-%05d", (ultimo == null ? 0 : ultimo) + 1);
        UUID id = UUID.randomUUID();
        String observaciones = r.observaciones() == null || r.observaciones().isBlank() ? null : r.observaciones().trim();
        jdbc.update("""
                insert into presupuesto (budget_id, budget_code, budget_period_id, requester_user_id, observations)
                values (?, ?, ?, ?, ?)""", id, codigo, r.periodId(), yo, observaciones);
        auditoria.registrar("PRESUPUESTO", id, "CREAR", null, Map.of("codigo", codigo, "periodo", r.periodId()), yo);
        return obtener(id, principal);
    }

    /** Reemplaza las partidas de la version de trabajo (SKU base x 6 meses). */
    @Transactional
    public PresupuestoDetalleDto guardarLineas(UUID id, List<LineaRequest> lineas, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, true);
        exigirEdicion(c, principal);
        Set<UUID> vistos = new HashSet<>();
        for (LineaRequest l : lineas) {
            if (!vistos.add(l.productId())) {
                throw ApiException.badRequest("Un SKU solo puede tener una partida.");
            }
            if (l.meses().stream().allMatch(q -> q.signum() == 0)) {
                throw ApiException.badRequest("Cada partida debe tener al menos un mes con cantidad mayor que cero.");
            }
        }
        // Los SKU nuevos deben estar activos; los ya presupuestados se conservan aunque se inactiven.
        Set<UUID> anteriores = new HashSet<>(jdbc.queryForList(
                "select product_id from presupuesto_linea where budget_id = ?", UUID.class, id));
        Set<UUID> nuevos = new HashSet<>(vistos);
        nuevos.removeAll(anteriores);
        if (!nuevos.isEmpty()) {
            List<UUID> activos = named.queryForList("select product_id from producto where active and product_id in (:ids)",
                    Map.of("ids", nuevos), UUID.class);
            if (activos.size() != nuevos.size()) {
                throw ApiException.badRequest("Selecciona SKU activos del catalogo.");
            }
        }
        jdbc.update("delete from presupuesto_linea where budget_id = ?", id);
        jdbc.batchUpdate("""
                insert into presupuesto_linea (budget_id, product_id, qty_m1, qty_m2, qty_m3, qty_m4, qty_m5, qty_m6)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", lineas, lineas.size(), (ps, l) -> {
            ps.setObject(1, id);
            ps.setObject(2, l.productId());
            for (int m = 0; m < 6; m++) {
                ps.setBigDecimal(m + 3, l.meses().get(m));
            }
        });
        jdbc.update("update presupuesto set updated_at = now() where budget_id = ?", id);
        auditoria.registrar("PRESUPUESTO", id, "PROGRAMACION", Map.of("partidas", anteriores.size()),
                Map.of("partidas", lineas.size()), principal.userId());
        return obtener(id, principal);
    }

    @Transactional
    public PresupuestoDetalleDto emitir(UUID id, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, true);
        exigirEdicion(c, principal);
        Map<String, Object> resumen = jdbc.queryForMap("""
                select count(*) as partidas,
                       string_agg(p.sku_base, ', ' order by p.sku_base) filter (where pv.sale_price is null) as sin_precio
                from presupuesto_linea l
                join producto p on p.product_id = l.product_id
                left join precio_venta_producto pv on pv.company_id = ? and pv.product_id = l.product_id and pv.active
                where l.budget_id = ?""", c.companyId(), id);
        if (((Number) resumen.get("partidas")).intValue() == 0) {
            throw ApiException.badRequest("Agrega al menos una partida antes de emitir.");
        }
        if (resumen.get("sin_precio") != null) {
            throw ApiException.badRequest("Sin precio de venta vigente: " + resumen.get("sin_precio")
                    + ". PYC o Logistica lo registran en el Catalogo.");
        }
        UUID aprobador = c.cecoApprover();
        if (aprobador == null) {
            throw ApiException.badRequest("El CECO no tiene aprobador asignado: Administracion debe configurarlo.");
        }
        if (aprobador.equals(c.requesterId())) {
            throw ApiException.badRequest("El solicitante no puede aprobar su propio presupuesto: asigna otro aprobador al CECO.");
        }
        Integer valido = jdbc.queryForObject("""
                select count(*) from usuario u
                join usuario_rol ur on ur.user_id = u.user_id
                join rol r on r.role_id = ur.role_id
                where u.user_id = ? and u.active and r.code in ('APROBADOR', 'GERENCIA')""", Integer.class, aprobador);
        if (valido == null || valido == 0) {
            throw ApiException.badRequest("El aprobador del CECO esta inactivo o ya no tiene rol Aprobador o Gerencia.");
        }
        jdbc.update("""
                update presupuesto set status = 'EMITIDO', approver_user_id = ?, issued_at = now(),
                  decision_reason = null, updated_at = now()
                where budget_id = ?""", aprobador, id);
        auditoria.registrar("PRESUPUESTO", id, "EMITIR", Map.of("estado", c.estado()),
                Map.of("estado", EMITIDO, "aprobador", aprobador), principal.userId());
        return obtener(id, principal);
    }

    /** Un presupuesto aprobado pasa a revision; sigue rigiendo su ultima version aprobada. */
    @Transactional
    public PresupuestoDetalleDto modificar(UUID id, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, true);
        if (!APROBADO.equals(c.estado())) {
            throw ApiException.badRequest("Solo se modifica un presupuesto aprobado.");
        }
        if (!puedeEscribir(c, principal)) {
            throw ApiException.forbidden("Sin permiso para modificar este presupuesto.");
        }
        jdbc.update("update presupuesto set status = 'MODIFICADO', updated_at = now() where budget_id = ?", id);
        auditoria.registrar("PRESUPUESTO", id, "MODIFICAR", Map.of("estado", APROBADO),
                Map.of("estado", MODIFICADO), principal.userId());
        return obtener(id, principal);
    }

    // ---------------------------------------------------------------- decision

    @Transactional
    public PresupuestoDetalleDto decidir(UUID id, String decision, String motivo, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        if (!APROBADO.equals(decision) && !OBSERVADO.equals(decision)) {
            throw ApiException.badRequest("Decision no valida.");
        }
        Ctx c = ctx(id, principal, true);
        if (!EMITIDO.equals(c.estado())) {
            throw ApiException.badRequest("El presupuesto no esta pendiente de aprobacion.");
        }
        if (!esDecisor(c, principal)) {
            throw ApiException.forbidden("Solo el aprobador asignado del CECO o Gerencia deciden este presupuesto.");
        }
        if (OBSERVADO.equals(decision)) {
            if (motivo == null || motivo.isBlank()) {
                throw ApiException.badRequest("Indica el motivo de la observacion.");
            }
            jdbc.update("""
                    update presupuesto set status = 'OBSERVADO', decision_reason = ?, updated_at = now()
                    where budget_id = ?""", motivo.trim(), id);
            auditoria.registrar("PRESUPUESTO", id, "OBSERVAR", Map.of("estado", EMITIDO),
                    Map.of("estado", OBSERVADO, "motivo", motivo.trim()), principal.userId());
        } else {
            Congelada v = congelar(c, principal);
            auditoria.registrar("PRESUPUESTO", id, "APROBAR", Map.of("estado", EMITIDO),
                    Map.of("estado", APROBADO, "version", v.numero()), principal.userId());
            // DEC-L049: un REQ en borrador por mes programado.
            borradores.generar(id, v.id(), principal.userId());
        }
        return obtener(id, principal);
    }

    /**
     * Congela una version nueva: cantidades, precio de venta vigente (obligatorio)
     * y el ultimo precio de compra de la empresa (OC no anulada; null si no es PEN
     * o no hay compra, nunca se inventa).
     */
    private record Congelada(UUID id, int numero) {
    }

    private Congelada congelar(Ctx c, UsuarioPrincipal principal) {
        record Fila(UUID productId, String sku, BigDecimal[] meses, BigDecimal venta, BigDecimal costo,
                    String moneda, UUID orden) {
        }
        List<Fila> filas = jdbc.query("""
                select l.product_id, p.sku_base, l.qty_m1, l.qty_m2, l.qty_m3, l.qty_m4, l.qty_m5, l.qty_m6,
                       pv.sale_price, oc.unit_cost, oc.currency_code, oc.order_id
                from presupuesto_linea l
                join producto p on p.product_id = l.product_id
                left join precio_venta_producto pv on pv.company_id = ? and pv.product_id = l.product_id and pv.active
                left join lateral (
                  select oi.unit_cost, o.currency_code, o.order_id
                  from orden_item oi join orden_abastecimiento o on o.order_id = oi.order_id
                  where oi.product_id = l.product_id and o.company_id = ? and o.status <> 'ANULADA'
                  order by o.created_at desc, o.order_id desc limit 1) oc on true
                where l.budget_id = ?
                order by p.sku_base""", (rs, i) -> {
            BigDecimal[] meses = new BigDecimal[6];
            for (int m = 0; m < 6; m++) {
                meses[m] = rs.getBigDecimal(m + 3);
            }
            return new Fila(rs.getObject(1, UUID.class), rs.getString(2), meses, rs.getBigDecimal(9),
                    rs.getBigDecimal(10), rs.getString(11), rs.getObject(12, UUID.class));
        }, c.companyId(), c.companyId(), c.id());
        List<String> sinPrecio = filas.stream().filter(f -> f.venta() == null).map(Fila::sku).toList();
        if (filas.isEmpty() || !sinPrecio.isEmpty()) {
            throw ApiException.badRequest("No se puede aprobar: falta precio de venta vigente para "
                    + String.join(", ", sinPrecio) + ".");
        }
        Integer ultima = jdbc.queryForObject(
                "select coalesce(max(version_no), 0) from presupuesto_version where budget_id = ?", Integer.class, c.id());
        int version = (ultima == null ? 0 : ultima) + 1;
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                insert into presupuesto_version (budget_version_id, budget_id, version_no, approved_by)
                values (?, ?, ?, ?)""", versionId, c.id(), version, principal.userId());
        jdbc.batchUpdate("""
                insert into presupuesto_version_linea (budget_version_id, product_id, qty_m1, qty_m2, qty_m3, qty_m4,
                  qty_m5, qty_m6, sale_price, purchase_price, purchase_status, purchase_order_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", filas, filas.size(), (ps, f) -> {
            boolean enSoles = f.orden() != null && "PEN".equals(f.moneda());
            ps.setObject(1, versionId);
            ps.setObject(2, f.productId());
            for (int m = 0; m < 6; m++) {
                ps.setBigDecimal(m + 3, f.meses()[m]);
            }
            ps.setBigDecimal(9, f.venta());
            ps.setBigDecimal(10, enSoles ? f.costo() : null);
            ps.setString(11, f.orden() == null ? "SIN_COMPRA" : enSoles ? "CONGELADO" : "NO_VALORIZABLE_PEN");
            ps.setObject(12, f.orden());
        });
        jdbc.update("""
                update presupuesto set status = 'APROBADO', current_version_id = ?, decision_reason = null,
                  updated_at = now()
                where budget_id = ?""", versionId, c.id());
        return new Congelada(versionId, version);
    }

    @Transactional
    public PresupuestoDetalleDto anular(UUID id, String motivo, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, true);
        if (!permisos(c, principal, 0).anular()) {
            throw ApiException.forbidden(ANULADO.equals(c.estado()) ? "El presupuesto ya esta anulado."
                    : "Sin permiso para anular este presupuesto.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw ApiException.badRequest("Indica el motivo de la anulacion.");
        }
        jdbc.update("""
                update presupuesto set status = 'ANULADO', decision_reason = ?, updated_at = now()
                where budget_id = ?""", motivo.trim(), id);
        auditoria.registrar("PRESUPUESTO", id, "ANULAR", Map.of("estado", c.estado()),
                Map.of("estado", ANULADO, "motivo", motivo.trim()), principal.userId());
        return obtener(id, principal);
    }

    // ---------------------------------------------------------------- cumplimiento (RES-01/RES-02)

    /**
     * Cumplimiento de la version aprobada vigente (bcCompliance de la V38.7): por SKU
     * programado vs real (lo despachado a los REQ del CECO y semestre cuyo envio se
     * recepciono en destino), pendiente (pedido - real), saldo (programado - real) y
     * disponible (saldo - pendiente), valorizados con los precios congelados. Incluye
     * los SKU pedidos sin presupuesto. Precio de compra solo para PYC, GERENCIA y
     * LOGISTICA; el Excel es exclusivo de PYC en su ambito.
     */
    public CumplimientoDto cumplimiento(UUID id, UsuarioPrincipal principal) {
        exigirAcceso(principal);
        Ctx c = ctx(id, principal, false);
        if (c.currentVersionId() == null) {
            throw ApiException.badRequest("El presupuesto aun no tiene una version aprobada.");
        }
        Map<String, Object> cab = jdbc.queryForMap("""
                select e.code as empresa, c.code as ceco_code, c.name as ceco_nombre, pp.year, pp.semester, v.version_no
                from presupuesto b
                join periodo_presupuestal pp on pp.budget_period_id = b.budget_period_id
                join ceco c on c.ceco_id = pp.ceco_id
                join empresa e on e.company_id = c.company_id
                join presupuesto_version v on v.budget_version_id = b.current_version_id
                where b.budget_id = ?""", id);
        int anio = ((Number) cab.get("year")).intValue();
        int semestre = ((Number) cab.get("semester")).intValue();
        java.time.LocalDate desde = java.time.LocalDate.of(anio, semestre == 1 ? 1 : 7, 1);
        java.time.LocalDate hasta = desde.plusMonths(6).minusDays(1);
        boolean conCompra = principal.tieneAlgunRol(Roles.PYC, Roles.GERENCIA, Roles.LOGISTICA);

        List<CumplimientoFilaDto> filas = jdbc.query("""
                with reqs as (
                  select r.request_id from requerimiento r
                  where r.ceco_id = ? and r.status not in ('BORRADOR', 'RECHAZADO')
                    and """ + ControlPresupuestalService.FECHA_REQ + """
                 between ? and ?),
                pedido as (
                  select ri.product_id, sum(ri.qty_requested) as qty
                  from requerimiento_item ri join reqs on reqs.request_id = ri.request_id
                  group by ri.product_id),
                recibido as (
                  select si.product_id, sum(si.qty) as qty
                  from salida s join reqs on reqs.request_id = s.request_id
                  join salida_item si on si.issue_id = s.issue_id
                  where exists (select 1 from envio e where e.issue_id = s.issue_id
                                and e.status in ('RECEPCIONADO', 'CERRADO'))
                  group by si.product_id),
                base as (
                  select vl.product_id, vl.qty_m1 + vl.qty_m2 + vl.qty_m3 + vl.qty_m4 + vl.qty_m5 + vl.qty_m6 as programado,
                         vl.sale_price, vl.purchase_price, false as sin_presupuesto
                  from presupuesto_version_linea vl where vl.budget_version_id = ?
                  union all
                  select pe.product_id, 0, null, null, true from pedido pe
                  where not exists (select 1 from presupuesto_version_linea vl
                                    where vl.budget_version_id = ? and vl.product_id = pe.product_id))
                select b.product_id, f.name, sf.name, p.sku_base, p.name, u.code, b.sin_presupuesto, b.programado,
                       coalesce(rc.qty, 0), coalesce(pe.qty, 0), b.sale_price, b.purchase_price
                from base b
                join producto p on p.product_id = b.product_id
                join familia_producto f on f.family_id = p.family_id
                join subfamilia_producto sf on sf.subfamily_id = p.subfamily_id
                join unidad_medida u on u.uom_id = p.base_uom_id
                left join recibido rc on rc.product_id = b.product_id
                left join pedido pe on pe.product_id = b.product_id
                order by f.name, sf.name, p.sku_base""", (rs, i) -> {
            boolean sinPresupuesto = rs.getBoolean(7);
            BigDecimal programado = rs.getBigDecimal(8);
            BigDecimal real = rs.getBigDecimal(9);
            BigDecimal pendiente = rs.getBigDecimal(10).subtract(real).max(BigDecimal.ZERO);
            BigDecimal saldo = programado.subtract(real);
            BigDecimal disponible = saldo.subtract(pendiente);
            BigDecimal venta = rs.getBigDecimal(11);
            BigDecimal compra = conCompra ? rs.getBigDecimal(12) : null;
            BigDecimal[] cantidades = {programado, real, pendiente, saldo, disponible};
            return new CumplimientoFilaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), sinPresupuesto, programado, real, pendiente,
                    saldo, disponible, venta, montos(venta, cantidades, sinPresupuesto), compra,
                    conCompra ? montos(compra, cantidades, sinPresupuesto) : null);
        }, c.cecoId(), java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta), c.currentVersionId(),
                c.currentVersionId());

        Map<String, List<CumplimientoFilaDto>> porFamilia = new LinkedHashMap<>();
        filas.forEach(f -> porFamilia.computeIfAbsent(f.familia(), k -> new ArrayList<>()).add(f));
        List<ResumenFamiliaDto> resumen = new ArrayList<>();
        porFamilia.forEach((familia, grupo) -> resumen.add(resumir(familia, grupo, conCompra)));
        resumen.add(resumir("TOTAL", filas, conCompra));
        return new CumplimientoDto(c.id(), c.codigo(), (String) cab.get("empresa"), (String) cab.get("ceco_code"),
                (String) cab.get("ceco_nombre"), anio + "-S" + semestre, ((Number) cab.get("version_no")).intValue(),
                Instant.now(), conCompra, esPyc(principal) && c.enAmbito(), filas, resumen);
    }

    /** Excel de cumplimiento: exclusivo de PYC en su ambito (bcExportCompliance). */
    public record Archivo(byte[] contenido, String nombre) {
    }

    public Archivo cumplimientoExcel(UUID id, UsuarioPrincipal principal) {
        CumplimientoDto dto = cumplimiento(id, principal);
        if (!dto.puedeDescargar()) {
            throw ApiException.forbidden("Descarga exclusiva de PYC en su CECO autorizado.");
        }
        String nombre = ("Cumplimiento_" + dto.codigo() + "_" + dto.empresa() + "_" + dto.periodo())
                .replaceAll("[^a-zA-Z0-9_-]", "_") + ".xlsx";
        return new Archivo(CumplimientoExcel.generar(dto), nombre);
    }

    /** Excel de la programacion: lo descarga quien puede ver el presupuesto. */
    public Archivo excel(UUID id, UsuarioPrincipal principal) {
        PresupuestoDetalleDto dto = obtener(id, principal);
        String nombre = ("Presupuesto_" + dto.codigo() + "_" + dto.empresa() + "_" + dto.anio() + "-S" + dto.semestre())
                .replaceAll("[^a-zA-Z0-9_-]", "_") + ".xlsx";
        return new Archivo(PresupuestoExcel.generar(dto), nombre);
    }

    /** Cantidades x precio; precio null = SIN PRECIO, salvo lo programado de un SKU sin presupuesto (0). */
    private static List<BigDecimal> montos(BigDecimal precio, BigDecimal[] cantidades, boolean sinPresupuesto) {
        List<BigDecimal> m = new ArrayList<>(5);
        for (BigDecimal q : cantidades) {
            m.add(precio == null ? null : q.multiply(precio).setScale(2, RoundingMode.HALF_UP));
        }
        if (sinPresupuesto) {
            m.set(0, BigDecimal.ZERO);
        }
        return m;
    }

    private static ResumenFamiliaDto resumir(String familia, List<CumplimientoFilaDto> filas, boolean conCompra) {
        return new ResumenFamiliaDto(familia, sumar(filas.stream().map(CumplimientoFilaDto::venta).toList()),
                conCompra ? sumar(filas.stream().map(CumplimientoFilaDto::compra).toList()) : null);
    }

    /** Suma por columna; si alguna fila no tiene precio en esa columna, queda null (INCOMPLETO). */
    private static List<BigDecimal> sumar(List<List<BigDecimal>> montos) {
        List<BigDecimal> total = new ArrayList<>(Collections.nCopies(5, BigDecimal.ZERO));
        for (List<BigDecimal> fila : montos) {
            for (int i = 0; i < 5; i++) {
                BigDecimal acumulado = total.get(i);
                total.set(i, acumulado == null || fila.get(i) == null ? null : acumulado.add(fila.get(i)));
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- autorizacion

    private Ctx ctx(UUID id, UsuarioPrincipal principal, boolean bloquear) {
        List<Ctx> c = jdbc.query("select " + CTX_COLUMNAS + "\n" + CTX_FROM + "\nwhere b.budget_id = ?"
                        + (bloquear ? " for update of b" : ""),
                (rs, i) -> mapearCtx(rs), principal.userId(), principal.userId(), id);
        if (c.isEmpty() || !puedeVer(c.get(0), principal)) {
            throw ApiException.notFound("Presupuesto no disponible.");
        }
        return c.get(0);
    }

    private static Ctx mapearCtx(ResultSet rs) throws SQLException {
        return new Ctx(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                rs.getBoolean(7), rs.getString(8), rs.getObject(9, UUID.class), rs.getObject(10, UUID.class),
                rs.getObject(11, UUID.class), rs.getBoolean(12));
    }

    private static PermisosPresupuestoDto permisos(Ctx c, UsuarioPrincipal p, int partidas) {
        boolean escribe = puedeEscribir(c, p);
        boolean editar = escribe && EDITABLES.contains(c.estado());
        boolean decide = esDecisor(c, p);
        return new PermisosPresupuestoDto(editar, editar && partidas > 0, decide && EMITIDO.equals(c.estado()),
                escribe && APROBADO.equals(c.estado()),
                (decide && ANULABLES.contains(c.estado())) || (escribe && BORRADOR.equals(c.estado())));
    }

    private static void exigirEdicion(Ctx c, UsuarioPrincipal p) {
        if (c.cerrado()) {
            throw ApiException.badRequest("El periodo esta cerrado: solo consulta.");
        }
        if (!EDITABLES.contains(c.estado())) {
            throw ApiException.badRequest("Solo se edita un presupuesto en borrador, observado o en modificacion.");
        }
        if (!puedeEscribir(c, p)) {
            throw ApiException.forbidden("Sin permiso para editar este presupuesto.");
        }
    }

    private static boolean veTodo(UsuarioPrincipal p) {
        return p.tieneAlgunRol(Roles.LOGISTICA, Roles.ADMIN);
    }

    private static boolean esRedactor(UsuarioPrincipal p) {
        return !p.tieneAlgunRol(Roles.LOGISTICA) && p.tieneAlgunRol(Roles.PYC, Roles.GERENCIA, Roles.APROBADOR, Roles.SOLICITANTE);
    }

    private static boolean esPyc(UsuarioPrincipal p) {
        return !p.tieneAlgunRol(Roles.LOGISTICA) && p.tieneAlgunRol(Roles.PYC);
    }

    private static boolean puedeVer(Ctx c, UsuarioPrincipal p) {
        return veTodo(p) || c.enAmbito() || p.userId().equals(c.approverId()) || p.userId().equals(c.requesterId());
    }

    /** Redacta: PYC/GERENCIA y el APROBADOR del CECO en su ambito; el SOLICITANTE solo lo suyo. */
    private static boolean puedeEscribir(Ctx c, UsuarioPrincipal p) {
        if (c.cerrado() || !c.enAmbito() || p.tieneAlgunRol(Roles.LOGISTICA)) {
            return false;
        }
        UUID yo = p.userId();
        return p.tieneAlgunRol(Roles.PYC, Roles.GERENCIA)
                || (p.tieneAlgunRol(Roles.APROBADOR) && yo.equals(c.cecoApprover()))
                || (p.tieneAlgunRol(Roles.SOLICITANTE) && yo.equals(c.requesterId()));
    }

    /** Decide: el aprobador copiado al emitir o la GERENCIA del CECO; nunca el solicitante. */
    private static boolean esDecisor(Ctx c, UsuarioPrincipal p) {
        UUID yo = p.userId();
        if (c.cerrado() || p.tieneAlgunRol(Roles.LOGISTICA) || yo.equals(c.requesterId())) {
            return false;
        }
        return (yo.equals(c.approverId()) && p.tieneAlgunRol(Roles.APROBADOR, Roles.GERENCIA))
                || (p.tieneAlgunRol(Roles.GERENCIA) && c.enAmbito());
    }

    private static void exigirAcceso(UsuarioPrincipal p) {
        if (!p.tieneAlgunRol(Roles.SOLICITANTE, Roles.APROBADOR, Roles.GERENCIA, Roles.PYC, Roles.LOGISTICA, Roles.ADMIN)) {
            throw ApiException.forbidden("Sin permiso para ver presupuestos.");
        }
    }

    private static void exigirPyc(UsuarioPrincipal p) {
        if (!esPyc(p)) {
            throw ApiException.forbidden("Solo PYC habilita y cierra periodos presupuestales.");
        }
    }

    private void exigirCecoEnAmbito(UUID cecoId, UsuarioPrincipal p) {
        Integer n = jdbc.queryForObject("select count(*) from (" + AMBITO + ") a where a.ceco_id = ?",
                Integer.class, p.userId(), p.userId(), cecoId);
        if (n == null || n == 0) {
            throw ApiException.forbidden("El CECO no esta en tu ambito.");
        }
    }

    private static Instant instante(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t != null ? t.toInstant() : null;
    }
}
