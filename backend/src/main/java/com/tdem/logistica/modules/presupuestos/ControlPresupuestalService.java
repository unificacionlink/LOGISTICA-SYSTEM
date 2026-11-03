package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.presupuestos.dto.ControlPresupuestalDto;
import com.tdem.logistica.modules.presupuestos.dto.DesviacionDto;
import com.tdem.logistica.modules.presupuestos.dto.ExcepcionPresupuestalDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * Control presupuestal al enviar un requerimiento (Lote 15, DEC-L038; bcCheck de
 * presupuestos-control.js, REQ-02/REQ-03 de la V38.7).
 * <ul>
 *   <li>Solo si PYC activo el control en el periodo (CECO + semestre de la fecha
 *       requerida). Desactivado = no se valida ni se registra nada.</li>
 *   <li>Disponible por SKU = programado en la version aprobada vigente (aunque el
 *       presupuesto este en revision) - lo pedido por los otros REQ del CECO en el
 *       semestre que no estan en borrador ni rechazados.</li>
 *   <li>Desviaciones: sin presupuesto aprobado, SKU no presupuestado o exceso.
 *       Con desviaciones el envio exige justificacion (code EXCEPCION_PRESUPUESTAL)
 *       y queda como excepcion justificada, visible en el presupuesto.</li>
 * </ul>
 */
@Service
public class ControlPresupuestalService {

    public static final String CODIGO_EXCEPCION = "EXCEPCION_PRESUPUESTAL";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ControlPresupuestalService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Fecha que ubica al REQ en un semestre: la requerida o, si falta, la de creacion (hora Lima). */
    // Con espacios a los lados: va entre text blocks, que recortan el espacio final de cada linea.
    static final String FECHA_REQ = " coalesce(r.required_date, cast(r.created_at at time zone 'America/Lima' as date)) ";

    /** Corre dentro de la transaccion del envio: si lanza, el REQ no se envia. */
    @Transactional
    public void verificar(UUID requestId, String justificacion, UUID userId) {
        Map<String, Object> req = jdbc.queryForMap("select r.ceco_id, " + FECHA_REQ + " as fecha from requerimiento r "
                + "where r.request_id = ?", requestId);
        LocalDate fecha = ((java.sql.Date) req.get("fecha")).toLocalDate();
        int anio = fecha.getYear();
        int semestre = fecha.getMonthValue() <= 6 ? 1 : 2;
        // Bloquea el periodo: dos envios simultaneos no deben consumir el mismo saldo.
        List<Map<String, Object>> periodo = jdbc.queryForList("""
                select budget_period_id, mandatory_control from periodo_presupuestal
                where ceco_id = ? and year = ? and semester = ? for update""", req.get("ceco_id"), anio, semestre);
        if (periodo.isEmpty() || !(Boolean) periodo.get(0).get("mandatory_control")) {
            return;
        }
        UUID periodId = (UUID) periodo.get(0).get("budget_period_id");
        List<Map<String, Object>> presupuesto = jdbc.queryForList("""
                select budget_id, current_version_id from presupuesto
                where budget_period_id = ? and status <> 'ANULADO' and current_version_id is not null""", periodId);
        UUID budgetId = presupuesto.isEmpty() ? null : (UUID) presupuesto.get(0).get("budget_id");
        UUID versionId = presupuesto.isEmpty() ? null : (UUID) presupuesto.get(0).get("current_version_id");
        LocalDate desde = LocalDate.of(anio, semestre == 1 ? 1 : 7, 1);
        LocalDate hasta = desde.plusMonths(6).minusDays(1);

        record Fila(UUID productId, String sku, BigDecimal pedido, BigDecimal usado, BigDecimal programado,
                    BigDecimal precio) {
        }
        List<Fila> filas = jdbc.query("""
                with pedido as (
                  select product_id, sum(qty_requested) as qty from requerimiento_item where request_id = ?
                  group by product_id),
                usado as (
                  select ri.product_id, sum(ri.qty_requested) as qty
                  from requerimiento r join requerimiento_item ri on ri.request_id = r.request_id
                  where r.ceco_id = ? and r.request_id <> ? and r.status not in ('BORRADOR', 'RECHAZADO')
                    and """ + FECHA_REQ + """
                 between ? and ?
                  group by ri.product_id)
                select p.product_id, pr.sku_base, p.qty, coalesce(u.qty, 0),
                       vl.qty_m1 + vl.qty_m2 + vl.qty_m3 + vl.qty_m4 + vl.qty_m5 + vl.qty_m6, vl.sale_price
                from pedido p
                join producto pr on pr.product_id = p.product_id
                left join usado u on u.product_id = p.product_id
                left join presupuesto_version_linea vl
                  on vl.budget_version_id = cast(? as uuid) and vl.product_id = p.product_id
                order by pr.sku_base""",
                (rs, i) -> new Fila(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)),
                requestId, req.get("ceco_id"), requestId, java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta),
                versionId);

        List<DesviacionDto> desviaciones = new ArrayList<>();
        BigDecimal monto = BigDecimal.ZERO;
        for (Fila f : filas) {
            if (f.programado() == null) {
                desviaciones.add(new DesviacionDto(f.productId(), f.sku(), f.pedido(), BigDecimal.ZERO, f.pedido(),
                        budgetId == null ? "SIN_PRESUPUESTO_APROBADO" : "SKU_NO_PRESUPUESTADO"));
                continue;
            }
            monto = monto.add(f.pedido().multiply(f.precio()));
            BigDecimal disponible = f.programado().subtract(f.usado()).max(BigDecimal.ZERO);
            if (f.pedido().compareTo(disponible) > 0) {
                desviaciones.add(new DesviacionDto(f.productId(), f.sku(), f.pedido(), disponible,
                        f.pedido().subtract(disponible), "EXCESO"));
            }
        }
        String motivo = justificacion == null || justificacion.isBlank() ? null : justificacion.trim();
        if (!desviaciones.isEmpty() && motivo == null) {
            throw new ApiException("Excepcion presupuestal " + anio + "-S" + semestre + ": " + resumen(desviaciones)
                    + ". Justifica el envio para continuar.", CODIGO_EXCEPCION, HttpStatus.BAD_REQUEST);
        }
        jdbc.update("""
                insert into control_presupuestal_req (request_id, budget_period_id, budget_id, budget_version_id,
                  status, amount, justification, deviations, checked_by)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)""",
                requestId, periodId, budgetId, versionId, desviaciones.isEmpty() ? "CUBIERTO" : "EXCEPCION_JUSTIFICADA",
                budgetId == null ? null : monto.setScale(2, RoundingMode.HALF_UP),
                desviaciones.isEmpty() ? null : motivo, objectMapper.writeValueAsString(desviaciones), userId);
    }

    private static String resumen(List<DesviacionDto> desviaciones) {
        return desviaciones.stream().map(d -> switch (d.motivo()) {
            case "SIN_PRESUPUESTO_APROBADO" -> d.sku() + " sin presupuesto aprobado";
            case "SKU_NO_PRESUPUESTADO" -> d.sku() + " no presupuestado";
            default -> d.sku() + " excede en " + d.exceso().stripTrailingZeros().toPlainString()
                    + " (disponible " + d.disponible().stripTrailingZeros().toPlainString() + ")";
        }).reduce((a, b) -> a + "; " + b).orElse("");
    }

    /** Ultimo control del REQ, o null si nunca se valido. */
    public ControlPresupuestalDto ultimo(UUID requestId) {
        return jdbc.query("""
                select k.status, pp.year, pp.semester, k.budget_id, b.budget_code, k.amount, k.justification,
                       cast(k.deviations as text), u.display_name, k.checked_at
                from control_presupuestal_req k
                join periodo_presupuestal pp on pp.budget_period_id = k.budget_period_id
                left join presupuesto b on b.budget_id = k.budget_id
                left join usuario u on u.user_id = k.checked_by
                where k.request_id = ?
                order by k.checked_at desc limit 1""",
                (rs, i) -> new ControlPresupuestalDto(rs.getString(1), rs.getInt(2) + "-S" + rs.getInt(3),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getBigDecimal(6), rs.getString(7),
                        leer(rs.getString(8)), rs.getString(9), instante(rs.getTimestamp(10))), requestId)
                .stream().findFirst().orElse(null);
    }

    /** REQ del periodo cuyo ultimo control fue excepcion justificada. */
    public List<ExcepcionPresupuestalDto> excepciones(UUID periodId) {
        return jdbc.query("""
                select k.request_id, r.request_code, r.status, us.display_name, k.justification,
                       cast(k.deviations as text), k.checked_at
                from (select distinct on (request_id) * from control_presupuestal_req
                      where budget_period_id = ? order by request_id, checked_at desc) k
                join requerimiento r on r.request_id = k.request_id
                left join usuario us on us.user_id = r.requester_user_id
                where k.status = 'EXCEPCION_JUSTIFICADA'
                order by k.checked_at desc""",
                (rs, i) -> new ExcepcionPresupuestalDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), leer(rs.getString(6)), instante(rs.getTimestamp(7))),
                periodId);
    }

    private List<DesviacionDto> leer(String json) {
        return json == null ? List.of() : List.of(objectMapper.readValue(json, DesviacionDto[].class));
    }

    private static java.time.Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
