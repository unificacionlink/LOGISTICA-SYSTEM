package com.tdem.logistica.modules.presupuestos;

import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DEC-L049: al aprobar un presupuesto se genera un requerimiento en BORRADOR por
 * cada mes con cantidades, a nombre del solicitante del presupuesto, para que solo
 * quite lo que no va y lo envie.
 * <ul>
 *   <li>Un REQ por presupuesto y mes (indice unico). Si el presupuesto se vuelve a
 *       aprobar, el borrador que siga en BORRADOR se rehace con la version nueva;
 *       el que ya se envio no se toca.</li>
 *   <li>Los meses ya terminados no generan borrador. La fecha requerida es el
 *       primer dia del mes (o hoy, si el mes ya empezo): asi el control
 *       presupuestal lo cuenta en su semestre.</li>
 *   <li>Producto con una sola variante activa: va con ella. Con tallas/colores:
 *       sin variante, y el solicitante la elige al editar (enviar lo exige).</li>
 * </ul>
 */
@Service
public class BorradoresPresupuestoService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String OBS_ELEGIR_VARIANTE = "Elegir talla/color";

    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public BorradoresPresupuestoService(JdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Resultado: borradores creados y rehechos. */
    public record Resultado(int creados, int actualizados) {
        public int total() {
            return creados + actualizados;
        }
    }

    private record Cabecera(String codigo, UUID requesterId, UUID cecoId, UUID companyId, String empresaCode,
                            int anio, int semestre, int version) {
    }

    private record Linea(UUID productId, UUID uomId, UUID variantId, BigDecimal[] meses) {
    }

    private record Existente(UUID requestId, String status) {
    }

    @Transactional
    public Resultado generar(UUID budgetId, UUID versionId, UUID usuarioId) {
        Cabecera cab = jdbc.queryForObject("""
                select b.budget_code, b.requester_user_id, pp.ceco_id, c.company_id, e.code, pp.year, pp.semester,
                       v.version_no
                from presupuesto b
                join periodo_presupuestal pp on pp.budget_period_id = b.budget_period_id
                join ceco c on c.ceco_id = pp.ceco_id
                join empresa e on e.company_id = c.company_id
                join presupuesto_version v on v.budget_version_id = ? and v.budget_id = b.budget_id
                where b.budget_id = ?""", (rs, i) -> new Cabecera(rs.getString(1), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5), rs.getInt(6),
                rs.getInt(7), rs.getInt(8)), versionId, budgetId);

        // Una consulta: lineas de la version + la variante si el producto tiene una sola activa.
        List<Linea> lineas = jdbc.query("""
                select l.product_id, p.base_uom_id, uv.variant_id,
                       l.qty_m1, l.qty_m2, l.qty_m3, l.qty_m4, l.qty_m5, l.qty_m6
                from presupuesto_version_linea l
                join producto p on p.product_id = l.product_id
                left join lateral (
                  select case when count(*) = 1 then (array_agg(pv.variant_id))[1] end as variant_id
                  from producto_variante pv where pv.product_id = l.product_id and pv.active) uv on true
                where l.budget_version_id = ?
                order by p.sku_base""", (rs, i) -> {
            BigDecimal[] meses = new BigDecimal[6];
            for (int m = 0; m < 6; m++) {
                meses[m] = rs.getBigDecimal(m + 4);
            }
            return new Linea(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                    meses);
        }, versionId);

        Map<Integer, Existente> existentes = new HashMap<>();
        jdbc.query("select budget_month, request_id, status from requerimiento where budget_id = ?",
                rs -> {
                    existentes.put(rs.getInt(1), new Existente(rs.getObject(2, UUID.class), rs.getString(3)));
                }, budgetId);

        LocalDate hoy = LocalDate.now(LIMA);
        int creados = 0;
        int actualizados = 0;
        for (int mes = 1; mes <= 6; mes++) {
            int m = mes - 1;
            List<Linea> delMes = lineas.stream()
                    .filter(l -> l.meses()[m] != null && l.meses()[m].signum() > 0).toList();
            if (delMes.isEmpty()) {
                continue;
            }
            YearMonth ym = MesesSemestre.mes(cab.anio(), cab.semestre(), mes);
            String justificacion = "Programación " + cab.codigo() + " v" + cab.version() + " · "
                    + MesesSemestre.nombre(cab.semestre(), mes) + " " + cab.anio();
            Existente previo = existentes.get(mes);
            UUID requestId;
            if (previo != null) {
                if (!"BORRADOR".equals(previo.status())) {
                    continue; // ya se envio: no se duplica ni se cambia
                }
                requestId = previo.requestId();
                jdbc.update("""
                        update requerimiento set budget_version_id = ?, justification = ?
                        where request_id = ?""", versionId, justificacion, requestId);
                jdbc.update("delete from requerimiento_item where request_id = ?", requestId);
                actualizados++;
            } else {
                if (ym.atEndOfMonth().isBefore(hoy)) {
                    continue; // mes vencido
                }
                LocalDate requerida = ym.atDay(1).isBefore(hoy) ? hoy : ym.atDay(1);
                requestId = UUID.randomUUID();
                jdbc.update("""
                        insert into requerimiento (request_id, request_code, company_id, ceco_id, requester_user_id,
                          requester_type, priority, purpose, justification, status, required_date,
                          budget_id, budget_version_id, budget_month)
                        values (?, ?, ?, ?, ?, 'INTERNO', 'NORMAL', 'CONSUMO', ?, 'BORRADOR', ?, ?, ?, ?)""",
                        requestId, RequerimientoService.generarRequestCode(cab.empresaCode()), cab.companyId(),
                        cab.cecoId(), cab.requesterId(), justificacion, requerida, budgetId, versionId,
                        (short) mes);
                creados++;
            }
            UUID reqId = requestId;
            jdbc.batchUpdate("""
                    insert into requerimiento_item (request_item_id, request_id, product_id, variant_id, uom_id,
                      qty_requested, observation)
                    values (?, ?, ?, ?, ?, ?, ?)""", delMes, delMes.size(), (ps, l) -> {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, reqId);
                ps.setObject(3, l.productId());
                ps.setObject(4, l.variantId());
                ps.setObject(5, l.uomId());
                ps.setBigDecimal(6, l.meses()[m]);
                ps.setString(7, l.variantId() == null ? OBS_ELEGIR_VARIANTE : null);
            });
            auditoria.registrar("REQUERIMIENTO", requestId, "GENERAR_DESDE_PRESUPUESTO", null,
                    Map.of("presupuesto", cab.codigo(), "version", cab.version(), "mes", mes,
                            "items", delMes.size()), usuarioId);
        }
        return new Resultado(creados, actualizados);
    }
}
