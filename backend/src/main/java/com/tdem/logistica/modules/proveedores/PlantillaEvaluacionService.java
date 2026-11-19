package com.tdem.logistica.modules.proveedores;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores.CriterioPlantilla;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores.GuardarPlantilla;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores.OpcionPlantilla;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores.Requisito;
import com.tdem.logistica.modules.proveedores.dto.ConfiguracionProveedores.RequisitoInput;
import com.tdem.logistica.modules.proveedores.dto.PlantillaDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.tdem.logistica.modules.proveedores.AccesoProveedores.exigir;
import static com.tdem.logistica.modules.proveedores.AccesoProveedores.limpio;

/**
 * Plantillas de calificacion (DEC-L070) y requisitos de documentos (DEC-L071),
 * configurables por Logistica. Cambiar una plantilla sube su version; las
 * evaluaciones ya registradas conservan sus pesos copiados.
 */
@Service
@Transactional(readOnly = true)
public class PlantillaEvaluacionService {

    private static final List<String> TIPOS = List.of("SELECCION", "DESEMPENO");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Auditoria auditoria;

    public PlantillaEvaluacionService(JdbcTemplate jdbc, ObjectMapper objectMapper, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditoria = auditoria;
    }

    public ConfiguracionProveedores.Datos configuracion(UsuarioPrincipal principal) {
        exigir(principal);
        return new ConfiguracionProveedores.Datos(TIPOS.stream().map(this::vigente).toList(), requisitos());
    }

    /** Plantilla activa del tipo, con sus criterios activos en orden. */
    PlantillaDto vigente(String kind) {
        List<PlantillaDto> plantillas = jdbc.query("""
                select template_id, kind, code, version, name, scale_max, approval_threshold, validity_months, alert_days
                  from plantilla_evaluacion where active and kind = ?""",
                (rs, n) -> new PlantillaDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getInt(8),
                        rs.getInt(9), List.of()), kind);
        if (plantillas.isEmpty()) {
            throw ApiException.badRequest("No hay una plantilla vigente de " + etiqueta(kind) + ".");
        }
        PlantillaDto t = plantillas.get(0);
        List<PlantillaDto.Criterio> criterios = jdbc.query("""
                select criterion_id, sort_order, name, weight, options::text
                  from plantilla_criterio where template_id = ? and active order by sort_order""",
                (rs, n) -> new PlantillaDto.Criterio(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
                        rs.getBigDecimal(4), List.of(objectMapper.readValue(rs.getString(5), PlantillaDto.Opcion[].class))),
                t.templateId());
        return new PlantillaDto(t.templateId(), t.kind(), t.code(), t.version(), t.name(), t.scaleMax(),
                t.approvalThreshold(), t.validityMonths(), t.alertDays(), criterios);
    }

    @Transactional
    public PlantillaDto guardarPlantilla(String kind, GuardarPlantilla req, UsuarioPrincipal principal) {
        exigir(principal);
        if (!TIPOS.contains(kind)) {
            throw ApiException.badRequest("Tipo de plantilla inválido.");
        }
        if (req.scaleMax().signum() <= 0 || req.approvalThreshold().signum() < 0
                || req.approvalThreshold().compareTo(req.scaleMax()) > 0) {
            throw ApiException.badRequest("La nota aprobatoria debe estar entre 0 y la escala máxima.");
        }
        CalificacionProveedor.validarPesos(req.criterios().stream().map(CriterioPlantilla::weight).toList());
        for (CriterioPlantilla c : req.criterios()) {
            if (c.weight().signum() <= 0) {
                throw ApiException.badRequest("Cada criterio necesita un peso mayor a 0.");
            }
            Set<BigDecimal> puntajes = new HashSet<>();
            for (OpcionPlantilla o : c.opciones() == null ? List.<OpcionPlantilla>of() : c.opciones()) {
                if (o.score().signum() < 0 || o.score().compareTo(req.scaleMax()) > 0) {
                    throw ApiException.badRequest("Los puntajes de «" + c.name().trim() + "» deben estar entre 0 y "
                            + req.scaleMax().stripTrailingZeros().toPlainString() + ".");
                }
                if (!puntajes.add(o.score().stripTrailingZeros())) {
                    throw ApiException.badRequest("«" + c.name().trim() + "» repite un puntaje.");
                }
            }
        }

        PlantillaDto antes = vigente(kind);
        UUID templateId = antes.templateId();
        jdbc.update("""
                update plantilla_evaluacion
                   set name = ?, code = ?, scale_max = ?, approval_threshold = ?, validity_months = ?, alert_days = ?,
                       version = version + 1, updated_by = ?, updated_at = now()
                 where template_id = ?""",
                req.name().trim(), req.code().trim(), req.scaleMax(), req.approvalThreshold(), req.validityMonths(),
                req.alertDays(), principal.userId(), templateId);

        Set<UUID> conservados = new HashSet<>();
        int orden = 1;
        for (CriterioPlantilla c : req.criterios()) {
            String opciones = objectMapper.writeValueAsString(c.opciones() == null ? List.of() : c.opciones().stream()
                    .map(o -> Map.of("score", o.score(), "label", o.label().trim())).toList());
            boolean existe = c.criterionId() != null && antes.criterios().stream()
                    .anyMatch(x -> x.criterionId().equals(c.criterionId()));
            if (existe) {
                jdbc.update("""
                        update plantilla_criterio set sort_order = ?, name = ?, weight = ?, options = cast(? as jsonb)
                         where criterion_id = ?""", orden, c.name().trim(), c.weight(), opciones, c.criterionId());
                conservados.add(c.criterionId());
            } else {
                jdbc.update("""
                        insert into plantilla_criterio (template_id, sort_order, name, weight, options)
                        values (?, ?, ?, ?, cast(? as jsonb))""", templateId, orden, c.name().trim(), c.weight(), opciones);
            }
            orden++;
        }
        for (PlantillaDto.Criterio c : antes.criterios()) {
            if (!conservados.contains(c.criterionId())) {
                // Se desactiva, no se borra: es referencia de la plantilla historica.
                jdbc.update("update plantilla_criterio set active = false where criterion_id = ?", c.criterionId());
            }
        }
        PlantillaDto despues = vigente(kind);
        auditoria.registrar("PLANTILLA_EVALUACION", templateId, "EDITAR",
                Map.of("version", antes.version(), "umbral", antes.approvalThreshold(), "criterios", resumen(antes)),
                Map.of("version", despues.version(), "umbral", despues.approvalThreshold(), "criterios", resumen(despues)),
                principal.userId());
        return despues;
    }

    List<Requisito> requisitos() {
        return jdbc.query("""
                select requirement_id, name, description, required, has_expiry, sort_order, active
                  from requisito_documento_proveedor order by active desc, sort_order, name""",
                (rs, n) -> new Requisito(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getBoolean(4), rs.getBoolean(5), rs.getInt(6), rs.getBoolean(7)));
    }

    /** Alta y edicion de requisitos; los que sobran se desactivan (los documentos siguen apuntandolos). */
    @Transactional
    public List<Requisito> guardarRequisitos(List<RequisitoInput> entrada, UsuarioPrincipal principal) {
        exigir(principal);
        List<Requisito> antes = requisitos();
        Set<UUID> vistos = new HashSet<>();
        int orden = 1;
        for (RequisitoInput r : entrada) {
            boolean existe = r.requirementId() != null
                    && antes.stream().anyMatch(x -> x.requirementId().equals(r.requirementId()));
            if (existe) {
                jdbc.update("""
                        update requisito_documento_proveedor
                           set name = ?, description = ?, required = ?, has_expiry = ?, sort_order = ?, active = ?
                         where requirement_id = ?""", r.name().trim(), limpio(r.description()), r.required(),
                        r.hasExpiry(), orden, r.active(), r.requirementId());
                vistos.add(r.requirementId());
            } else {
                jdbc.update("""
                        insert into requisito_documento_proveedor (name, description, required, has_expiry, sort_order, active)
                        values (?, ?, ?, ?, ?, ?)""", r.name().trim(), limpio(r.description()), r.required(),
                        r.hasExpiry(), orden, r.active());
            }
            orden++;
        }
        for (Requisito r : antes) {
            if (!vistos.contains(r.requirementId()) && r.active()) {
                jdbc.update("update requisito_documento_proveedor set active = false where requirement_id = ?",
                        r.requirementId());
            }
        }
        List<Requisito> despues = requisitos();
        auditoria.registrar("REQUISITO_DOCUMENTO_PROVEEDOR", new UUID(0, 0), "EDITAR",
                Map.of("requisitos", antes.stream().filter(Requisito::active).map(Requisito::name).toList()),
                Map.of("requisitos", despues.stream().filter(Requisito::active).map(Requisito::name).toList()),
                principal.userId());
        return despues;
    }

    private static List<String> resumen(PlantillaDto t) {
        return t.criterios().stream()
                .map(c -> c.name() + " (" + c.weight().stripTrailingZeros().toPlainString() + "%)").toList();
    }

    static String etiqueta(String kind) {
        return "SELECCION".equals(kind) ? "selección" : "evaluación de desempeño";
    }
}
