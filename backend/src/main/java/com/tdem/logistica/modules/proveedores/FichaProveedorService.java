package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.AppConstants.EstadoProveedor;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.EvaluacionResumenDto;
import com.tdem.logistica.modules.proveedores.dto.FichaProveedorDto;
import com.tdem.logistica.modules.proveedores.dto.PanelProveedoresDto;
import com.tdem.logistica.modules.proveedores.dto.SolicitudesProveedor.Historial;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;

/**
 * Lecturas del modulo (DEC-L069/L070): pantalla principal con indicadores,
 * ficha con advertencias para habilitar, e historial de decisiones (leido de
 * log_cambios, sin tabla propia). La vigencia se calcula al leer: nunca se
 * guarda ni cambia el estado del proveedor.
 */
@Service
@Transactional(readOnly = true)
public class FichaProveedorService {

    private final JdbcTemplate jdbc;
    private final ProveedorService proveedores;

    public FichaProveedorService(JdbcTemplate jdbc, ProveedorService proveedores) {
        this.jdbc = jdbc;
        this.proveedores = proveedores;
    }

    /** Dias de aviso antes del vencimiento: los de la plantilla vigente de desempeno. */
    int diasAviso() {
        List<Integer> dias = jdbc.queryForList(
                "select alert_days from plantilla_evaluacion where active and kind = 'DESEMPENO'", Integer.class);
        return dias.isEmpty() ? 30 : dias.get(0);
    }

    /** Una sola consulta para toda la pantalla (la BD esta lejos). */
    public PanelProveedoresDto panel(UsuarioPrincipal principal) {
        exigir(principal);
        LocalDate hoy = VigenciaEvaluacion.hoy();
        int aviso = diasAviso();
        List<PanelProveedoresDto.Fila> filas = jdbc.query("""
                select p.supplier_id, p.ruc, p.legal_name, p.trade_name,
                       coalesce(p.contact_name, p.email, p.whatsapp_phone, p.phone) as contacto,
                       p.supplier_type, p.status, p.active, p.city,
                       (select count(*) from proveedor_producto pp where pp.supplier_id = p.supplier_id) as productos,
                       (select string_agg(case when pc.family_id is null then t.name else t.name || ' / ' || f.name end,
                                          ' · ' order by t.name, f.name)
                          from proveedor_categoria pc
                          join tipo_producto t on t.product_type_id = pc.product_type_id
                          left join familia_producto f on f.family_id = pc.family_id
                         where pc.supplier_id = p.supplier_id) as categorias,
                       (select string_agg(pr.sku_base || ' ' || pr.name, ' | ')
                          from proveedor_producto pp join producto pr on pr.product_id = pp.product_id
                         where pp.supplier_id = p.supplier_id) as busqueda,
                       e.final_score, e.scale_max, e.result, e.kind, e.evaluated_at, e.next_evaluation_date
                  from proveedor p
                  left join lateral (
                        select ev.final_score, ev.scale_max, ev.result, ev.kind, ev.evaluated_at, ev.next_evaluation_date
                          from evaluacion_proveedor ev
                         where ev.supplier_id = p.supplier_id and ev.status = 'REGISTRADA'
                         order by ev.evaluated_at desc, ev.created_at desc
                         limit 1) e on true
                 order by p.legal_name""",
                (rs, n) -> {
                    LocalDate proxima = fecha(rs, "next_evaluation_date");
                    return new PanelProveedoresDto.Fila(
                            rs.getObject("supplier_id", UUID.class), rs.getString("ruc"), rs.getString("legal_name"),
                            rs.getString("trade_name"), rs.getString("contacto"), rs.getString("supplier_type"),
                            rs.getString("status"), rs.getBoolean("active"), rs.getString("city"),
                            rs.getInt("productos"), rs.getString("categorias"), rs.getString("busqueda"),
                            rs.getBigDecimal("final_score"), rs.getBigDecimal("scale_max"), rs.getString("result"),
                            rs.getString("kind"), fecha(rs, "evaluated_at"), proxima,
                            VigenciaEvaluacion.estado(proxima, hoy, aviso));
                });

        int habilitados = 0;
        int enRevision = 0;
        int porVencer = 0;
        int vencidas = 0;
        int sinEvaluacion = 0;
        for (PanelProveedoresDto.Fila f : filas) {
            if (EstadoProveedor.PENDIENTE_REVISION.equals(f.status())) {
                enRevision++;
            }
            if (!EstadoProveedor.HABILITADO.equals(f.status()) || !f.active()) {
                continue;
            }
            habilitados++;
            switch (f.vigencia()) {
                case VigenciaEvaluacion.PROXIMO_A_VENCER -> porVencer++;
                case VigenciaEvaluacion.VENCIDO -> vencidas++;
                case VigenciaEvaluacion.SIN_EVALUACION -> sinEvaluacion++;
                default -> {
                }
            }
        }
        return new PanelProveedoresDto(hoy, new PanelProveedoresDto.Kpis(filas.size(), habilitados, enRevision,
                porVencer, vencidas, sinEvaluacion), filas);
    }

    public FichaProveedorDto ficha(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(id);
        List<FichaProveedorDto.Categoria> categorias = jdbc.query("""
                select pc.product_type_id, pc.family_id, t.name, f.name
                  from proveedor_categoria pc
                  join tipo_producto t on t.product_type_id = pc.product_type_id
                  left join familia_producto f on f.family_id = pc.family_id
                 where pc.supplier_id = ?
                 order by t.name, f.name nulls first""",
                (rs, n) -> new FichaProveedorDto.Categoria(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4)), id);
        List<FichaProveedorDto.ProductoOfrecido> productos = jdbc.query("""
                select pr.product_id, pr.sku_base, pr.name, t.name, f.name
                  from proveedor_producto pp
                  join producto pr on pr.product_id = pp.product_id
                  join tipo_producto t on t.product_type_id = pr.product_type_id
                  join familia_producto f on f.family_id = pr.family_id
                 where pp.supplier_id = ?
                 order by pr.sku_base""",
                (rs, n) -> new FichaProveedorDto.ProductoOfrecido(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)), id);
        EvaluacionResumenDto seleccion = ultima(id, "SELECCION");
        EvaluacionResumenDto desempeno = ultima(id, "DESEMPENO");
        EvaluacionResumenDto vigente = masReciente(seleccion, desempeno);
        LocalDate proxima = vigente != null ? vigente.nextEvaluationDate() : null;
        return new FichaProveedorDto(ProveedorMapper.toDto(p), categorias, productos, seleccion, desempeno,
                VigenciaEvaluacion.estado(proxima, VigenciaEvaluacion.hoy(), diasAviso()), proxima,
                advertencias(id));
    }

    /**
     * Lo que falta para habilitar con todo en regla (DEC-L069). No bloquea:
     * habilitar con advertencias exige escribir el motivo, que queda auditado.
     */
    List<String> advertencias(UUID supplierId) {
        List<String> avisos = new ArrayList<>();
        EvaluacionResumenDto seleccion = ultima(supplierId, "SELECCION");
        if (seleccion == null) {
            avisos.add("No tiene ficha de selección (F-LOG-03) registrada.");
        } else if (CalificacionProveedor.NO_APROBADO.equals(seleccion.result())) {
            avisos.add("La selección obtuvo " + seleccion.finalScore().stripTrailingZeros().toPlainString()
                    + ", por debajo de la nota aprobatoria (" + seleccion.threshold().stripTrailingZeros().toPlainString() + ").");
        } else if (CalificacionProveedor.PENDIENTE_CONFORMIDAD.equals(seleccion.result())) {
            avisos.add("La selección tiene una conformidad pendiente (Logística o área usuaria).");
        }
        LocalDate hoy = VigenciaEvaluacion.hoy();
        jdbc.query("""
                select r.name,
                       (select max(coalesce(d.expires_at, date '9999-12-31'))
                          from proveedor_documento d
                         where d.supplier_id = ? and d.requirement_id = r.requirement_id and d.status = 'ACTIVO') as vence
                  from requisito_documento_proveedor r
                 where r.active and r.required
                 order by r.sort_order, r.name""",
                rs -> {
                    LocalDate vence = fecha(rs, "vence");
                    if (vence == null) {
                        avisos.add("Falta el documento obligatorio: " + rs.getString("name") + ".");
                    } else if (hoy.isAfter(vence)) {
                        avisos.add("Está vencido el documento obligatorio: " + rs.getString("name") + ".");
                    }
                }, supplierId);
        return avisos;
    }

    /** Ultima evaluacion registrada (no anulada) de ese tipo. */
    EvaluacionResumenDto ultima(UUID supplierId, String kind) {
        List<EvaluacionResumenDto> filas = jdbc.query("""
                select evaluation_id, kind, template_code, evaluated_at, final_score, scale_max, threshold, result,
                       next_evaluation_date
                  from evaluacion_proveedor
                 where supplier_id = ? and kind = ? and status = 'REGISTRADA'
                 order by evaluated_at desc, created_at desc
                 limit 1""",
                (rs, n) -> new EvaluacionResumenDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        fecha(rs, "evaluated_at"), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                        rs.getString(8), fecha(rs, "next_evaluation_date")),
                supplierId, kind);
        return filas.isEmpty() ? null : filas.get(0);
    }

    private static EvaluacionResumenDto masReciente(EvaluacionResumenDto a, EvaluacionResumenDto b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.evaluatedAt().isBefore(a.evaluatedAt()) ? a : b;
    }

    /** Decisiones y cambios del proveedor, del mas reciente al mas antiguo. */
    public List<Historial> historial(UUID id, UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(id);
        return jdbc.query("""
                select l.accion, l.antes ->> 'estado', l.despues ->> 'estado',
                       coalesce(l.despues ->> 'motivo', l.antes ->> 'motivo'), u.display_name, l.created_at
                  from log_cambios l
                  left join usuario u on u.user_id = l.usuario_id
                 where l.entidad = 'PROVEEDOR' and l.entidad_id = ?
                 order by l.created_at desc
                 limit 100""",
                (rs, n) -> {
                    Timestamp t = rs.getTimestamp(6);
                    return new Historial(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), t != null ? t.toInstant().atOffset(ZoneOffset.UTC) : null);
                }, id);
    }

    static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        Date d = rs.getDate(columna);
        return d != null ? d.toLocalDate() : null;
    }

    static BigDecimal cero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
