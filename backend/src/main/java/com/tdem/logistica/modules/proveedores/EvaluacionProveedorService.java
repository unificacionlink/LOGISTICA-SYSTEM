package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.AppConstants.EstadoProveedor;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Proveedor;
import com.tdem.logistica.modules.proveedores.dto.EvaluacionDto;
import com.tdem.logistica.modules.proveedores.dto.PlantillaDto;
import com.tdem.logistica.modules.proveedores.dto.RegistrarEvaluacionRequest;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigirMotivo;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.limpio;
import static com.tdem.logistica.modules.proveedores.FichaProveedorService.fecha;

/**
 * Seleccion inicial (F-LOG-03) y evaluacion de desempeno (F-LOG-07), DEC-L070.
 * Usa la plantilla vigente y copia cada criterio en la evaluacion. El resultado
 * y la proxima fecha son informativos: no habilitan, bloquean ni deshabilitan.
 */
@Service
@Transactional(readOnly = true)
public class EvaluacionProveedorService {

    private final JdbcTemplate jdbc;
    private final ProveedorService proveedores;
    private final PlantillaEvaluacionService plantillas;
    private final Auditoria auditoria;

    public EvaluacionProveedorService(JdbcTemplate jdbc, ProveedorService proveedores,
                                      PlantillaEvaluacionService plantillas, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.proveedores = proveedores;
        this.plantillas = plantillas;
        this.auditoria = auditoria;
    }

    @Transactional
    public EvaluacionDto registrar(UUID supplierId, RegistrarEvaluacionRequest req, UsuarioPrincipal principal) {
        exigir(principal);
        Proveedor p = proveedores.buscar(supplierId);
        if (EstadoProveedor.RECHAZADO.equals(p.getStatus())) {
            throw ApiException.badRequest("El proveedor está rechazado: reabre la solicitud para evaluarlo.");
        }
        if ("DESEMPENO".equals(req.kind()) && !EstadoProveedor.HABILITADO.equals(p.getStatus())) {
            throw ApiException.badRequest("La evaluación de desempeño es para proveedores habilitados; "
                    + "antes de habilitar registra la selección.");
        }
        LocalDate hoy = VigenciaEvaluacion.hoy();
        if (req.evaluatedAt().isAfter(hoy)) {
            throw ApiException.badRequest("La fecha de evaluación no puede ser futura.");
        }

        PlantillaDto t = plantillas.vigente(req.kind());
        Map<UUID, RegistrarEvaluacionRequest.Puntaje> puntajes = new HashMap<>();
        for (RegistrarEvaluacionRequest.Puntaje x : req.criterios()) {
            puntajes.put(x.criterionId(), x);
        }
        List<CalificacionProveedor.Linea> lineas = new ArrayList<>();
        List<Object[]> filas = new ArrayList<>();
        for (PlantillaDto.Criterio c : t.criterios()) {
            RegistrarEvaluacionRequest.Puntaje x = puntajes.get(c.criterionId());
            if (x == null) {
                throw ApiException.badRequest("Falta calificar: " + c.name());
            }
            BigDecimal score = x.score();
            String etiqueta = null;
            if (c.opciones().isEmpty()) {
                if (score.signum() < 0 || score.compareTo(t.scaleMax()) > 0) {
                    throw ApiException.badRequest("El puntaje de «" + c.name() + "» debe estar entre 0 y "
                            + t.scaleMax().stripTrailingZeros().toPlainString() + ".");
                }
            } else {
                PlantillaDto.Opcion opcion = c.opciones().stream()
                        .filter(o -> o.score().compareTo(score) == 0).findFirst()
                        .orElseThrow(() -> ApiException.badRequest("Elige una de las respuestas de «" + c.name() + "»."));
                etiqueta = opcion.label();
            }
            lineas.add(new CalificacionProveedor.Linea(c.weight(), score));
            filas.add(new Object[]{c.sortOrder(), c.name(), c.weight(), score, etiqueta, limpio(x.observation())});
        }
        if (puntajes.size() != t.criterios().size()) {
            throw ApiException.badRequest("La plantilla cambió: vuelve a abrir el formulario.");
        }
        CalificacionProveedor.validarPesos(t.criterios().stream().map(PlantillaDto.Criterio::weight).toList());
        BigDecimal nota = CalificacionProveedor.nota(lineas);
        String resultado = CalificacionProveedor.resultado(nota, t.approvalThreshold(), req.logisticsConformity(),
                req.userAreaConformity());
        LocalDate proxima = req.nextEvaluationDate() != null
                ? req.nextEvaluationDate()
                : req.evaluatedAt().plusMonths(t.validityMonths());
        if (proxima.isBefore(req.evaluatedAt())) {
            throw ApiException.badRequest("La próxima evaluación no puede ser anterior a esta.");
        }

        UUID evaluationId = UUID.randomUUID();
        jdbc.update("""
                insert into evaluacion_proveedor (evaluation_id, supplier_id, template_id, kind, template_code,
                       template_version, evaluated_at, evaluator_user_id, area, scope, user_area_reviewer,
                       logistics_conformity, user_area_conformity, final_score, scale_max, threshold, result,
                       observations, next_evaluation_date)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                evaluationId, supplierId, t.templateId(), t.kind(), t.code(), t.version(), req.evaluatedAt(),
                principal.userId(), limpio(req.area()), limpio(req.scope()), limpio(req.userAreaReviewer()),
                req.logisticsConformity(), req.userAreaConformity(), nota, t.scaleMax(), t.approvalThreshold(),
                resultado, limpio(req.observations()), proxima);
        for (Object[] f : filas) {
            jdbc.update("""
                    insert into evaluacion_criterio (evaluation_id, sort_order, criterion_name, weight, score,
                           score_label, observation)
                    values (?, ?, ?, ?, ?, ?, ?)""", evaluationId, f[0], f[1], f[2], f[3], f[4], f[5]);
        }
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("tipo", t.kind());
        despues.put("formato", t.code() + " v" + t.version());
        despues.put("nota", nota);
        despues.put("resultado", resultado);
        despues.put("proxima", proxima.toString());
        auditoria.registrar("PROVEEDOR", supplierId, "SELECCION".equals(t.kind()) ? "SELECCION" : "EVALUACION",
                null, despues, principal.userId());
        return listarInterno(supplierId).stream().filter(e -> e.evaluationId().equals(evaluationId))
                .findFirst().orElseThrow();
    }

    public List<EvaluacionDto> listar(UUID supplierId, UsuarioPrincipal principal) {
        exigir(principal);
        proveedores.buscar(supplierId);
        return listarInterno(supplierId);
    }

    /** Anular deja la evaluacion en el historial con su motivo; no se borra. */
    @Transactional
    public EvaluacionDto anular(UUID evaluationId, String motivo, UsuarioPrincipal principal) {
        exigir(principal);
        String m = exigirMotivo(motivo, "Escribe el motivo de la anulación.");
        List<Map<String, Object>> fila = jdbc.queryForList(
                "select supplier_id, status, kind, final_score from evaluacion_proveedor where evaluation_id = ?",
                evaluationId);
        if (fila.isEmpty()) {
            throw ApiException.notFound("Evaluación no encontrada.");
        }
        Map<String, Object> e = fila.get(0);
        if (!"REGISTRADA".equals(e.get("status"))) {
            throw ApiException.badRequest("La evaluación ya está anulada.");
        }
        UUID supplierId = (UUID) e.get("supplier_id");
        jdbc.update("""
                update evaluacion_proveedor set status = 'ANULADA', void_reason = ?, voided_by = ?, voided_at = now()
                 where evaluation_id = ?""", m, principal.userId(), evaluationId);
        auditoria.registrar("PROVEEDOR", supplierId, "ANULAR_EVALUACION",
                Map.of("tipo", e.get("kind"), "nota", e.get("final_score")),
                Map.of("motivo", m, "evaluacion", evaluationId.toString()), principal.userId());
        return listarInterno(supplierId).stream().filter(x -> x.evaluationId().equals(evaluationId))
                .findFirst().orElseThrow();
    }

    /** Cabeceras y criterios en dos consultas (sin N+1). */
    private List<EvaluacionDto> listarInterno(UUID supplierId) {
        Map<UUID, List<EvaluacionDto.Criterio>> criterios = new HashMap<>();
        jdbc.query("""
                select c.evaluation_id, c.sort_order, c.criterion_name, c.weight, c.score, c.score_label, c.observation
                  from evaluacion_criterio c
                  join evaluacion_proveedor e on e.evaluation_id = c.evaluation_id
                 where e.supplier_id = ?
                 order by c.sort_order""",
                rs -> {
                    criterios.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new EvaluacionDto.Criterio(rs.getInt(2), rs.getString(3), rs.getBigDecimal(4),
                                    rs.getBigDecimal(5), rs.getString(6), rs.getString(7)));
                }, supplierId);
        return jdbc.query("""
                select e.evaluation_id, e.kind, e.template_code, e.template_version, e.evaluated_at, u.display_name,
                       e.area, e.scope, e.user_area_reviewer, e.logistics_conformity, e.user_area_conformity,
                       e.final_score, e.scale_max, e.threshold, e.result, e.observations, e.next_evaluation_date,
                       e.status, e.void_reason, e.voided_at
                  from evaluacion_proveedor e
                  left join usuario u on u.user_id = e.evaluator_user_id
                 where e.supplier_id = ?
                 order by e.evaluated_at desc, e.created_at desc""",
                (rs, n) -> {
                    UUID id = rs.getObject("evaluation_id", UUID.class);
                    Timestamp anulada = rs.getTimestamp("voided_at");
                    return new EvaluacionDto(id, rs.getString("kind"), rs.getString("template_code"),
                            rs.getInt("template_version"), fecha(rs, "evaluated_at"), rs.getString("display_name"),
                            rs.getString("area"), rs.getString("scope"), rs.getString("user_area_reviewer"),
                            rs.getString("logistics_conformity"), rs.getString("user_area_conformity"),
                            rs.getBigDecimal("final_score"), rs.getBigDecimal("scale_max"), rs.getBigDecimal("threshold"),
                            rs.getString("result"), rs.getString("observations"), fecha(rs, "next_evaluation_date"),
                            rs.getString("status"), rs.getString("void_reason"),
                            anulada != null ? anulada.toInstant().atOffset(ZoneOffset.UTC) : null,
                            criterios.getOrDefault(id, List.of()));
                }, supplierId);
    }
}
