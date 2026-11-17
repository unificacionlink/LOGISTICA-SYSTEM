package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.dto.PersonaRequest;
import com.tdem.logistica.modules.identidad.dto.PersonalAdminDto;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.security.AccesoVigente;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mantenimiento del padron de personal (DEC-L064), solo ADMIN. Nadie se borra:
 * se inactiva con fecha y quien lo hizo, y solo un ADMIN lo reactiva.
 * <ul>
 *   <li>Alta y edicion solo en empresas que RR.HH. aun no cubre (DEC-L028); los
 *       datos de RR.HH. se corrigen alla.</li>
 *   <li>Inactivar vale para cualquier persona: su cuenta pierde acceso, salvo que
 *       siga activa en el padron de otra empresa. El sync no reactiva a quien
 *       inactivo un ADMIN.</li>
 *   <li>A quien cesa RR.HH. lo reactiva el sync cuando RR.HH. lo vuelva a enviar.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PadronPersonalService {

    private static final String FUENTE_MANUAL = "MANUAL";
    private static final Pattern DNI = Pattern.compile("^[0-9A-Z]{8,12}$");

    private final JdbcTemplate jdbc;
    private final EmpresaRepository empresaRepository;
    private final CecoRepository cecoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AccesoVigente accesoVigente;
    private final Auditoria auditoria;

    public PadronPersonalService(JdbcTemplate jdbc, EmpresaRepository empresaRepository, CecoRepository cecoRepository,
                                 UsuarioRepository usuarioRepository, AccesoVigente accesoVigente, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.empresaRepository = empresaRepository;
        this.cecoRepository = cecoRepository;
        this.usuarioRepository = usuarioRepository;
        this.accesoVigente = accesoVigente;
        this.auditoria = auditoria;
    }

    /** Todo el padron, activos e inactivos; la pantalla lo muestra por empresa, sin mezclar. */
    public List<PersonalAdminDto> listar(UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        return jdbc.query("""
                select p.personal_id, e.code, p.dni, p.full_name, coalesce(u.corporate_email, p.email),
                       p.position, p.ceco_id, c.code, c.name, p.fuente, p.user_id, p.active,
                       p.inactivated_at, quien.display_name
                  from personal p
                  join empresa e on e.company_id = p.company_id
                  left join ceco c on c.ceco_id = p.ceco_id
                  left join usuario u on u.user_id = p.user_id
                  left join usuario quien on quien.user_id = p.inactivated_by
                 order by e.code, p.full_name""",
                (rs, n) -> {
                    Timestamp inactivo = rs.getTimestamp(13);
                    return new PersonalAdminDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class),
                            rs.getString(8), rs.getString(9), rs.getString(10), rs.getObject(11, UUID.class),
                            rs.getBoolean(12), inactivo != null ? inactivo.toInstant() : null, rs.getString(14));
                });
    }

    // ---------------------------------------------------------------- alta y edicion

    @Transactional
    public List<PersonalAdminDto> crear(PersonaRequest r, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Empresa empresa = empresaManual(r.empresaCode());
        String dni = normalizarDni(r.dni());
        if (!DNI.matcher(dni).matches()) {
            throw ApiException.badRequest(dni.isEmpty() ? "Ingresa el DNI." : "DNI invalido (8 a 12 digitos o letras).");
        }
        List<Map<String, Object>> previa = jdbc.queryForList(
                "select full_name, active from personal where company_id = ? and dni = ?", empresa.getCompanyId(), dni);
        if (!previa.isEmpty()) {
            Object quien = previa.get(0).get("full_name");
            throw ApiException.badRequest(Boolean.TRUE.equals(previa.get(0).get("active"))
                    ? quien + " ya esta en el padron de " + empresa.getCode() + "."
                    : quien + " ya esta en el padron de " + empresa.getCode() + " como inactivo: reactivalo.");
        }
        String correo = correo(r.correo());
        UUID cuenta = usuarioRepository.findByEmployeeExternalId(dni).map(Usuario::getUserId).orElse(null);
        exigirCorreoLibre(correo, cuenta);
        UUID cecoId = cecoValido(r.cecoId(), empresa, null);
        String nombre = r.nombre().trim();
        String cargo = textoOpcional(r.cargo());

        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into personal (personal_id, company_id, dni, full_name, email, position, ceco_id, fuente, user_id)
                values (?, ?, ?, ?, ?, ?, ?, 'MANUAL', ?)""",
                id, empresa.getCompanyId(), dni, nombre, correo, cargo, cecoId, cuenta);
        Map<String, Object> despues = datos(nombre, correo, cargo, cecoId);
        despues.put("empresa", empresa.getCode());
        despues.put("dni", dni);
        auditoria.registrar("PERSONAL", id, "CREAR", null, despues, principal.userId());
        return listar(principal);
    }

    /** Datos de una persona MANUAL; si tiene cuenta MANUAL, la cuenta muestra lo mismo. */
    @Transactional
    public List<PersonalAdminDto> editar(UUID personalId, PersonaRequest r, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Ficha f = ficha(personalId);
        if (!FUENTE_MANUAL.equals(f.fuente())) {
            throw ApiException.badRequest(f.nombre() + " viene de RR.HH.: sus datos se corrigen alla.");
        }
        String correo = correo(r.correo());
        exigirCorreoLibre(correo, f.userId());
        Empresa empresa = empresaRepository.getReferenceById(f.companyId());
        UUID cecoId = cecoValido(r.cecoId(), empresa, f.cecoId());
        String nombre = r.nombre().trim();
        String cargo = textoOpcional(r.cargo());

        jdbc.update("update personal set full_name = ?, email = ?, position = ?, ceco_id = ? where personal_id = ?",
                nombre, correo, cargo, cecoId, personalId);
        Usuario u = f.userId() != null ? usuarioRepository.findById(f.userId()).orElse(null) : null;
        if (u != null && FUENTE_MANUAL.equals(u.getFuente())) {
            u.setDisplayName(nombre);
            u.setCargo(cargo);
            if (correo != null) {
                u.setCorporateEmail(correo);
            }
            usuarioRepository.saveAndFlush(u);
            // Solo agrega el CECO de planilla: no quita los que ADMIN asigno a mano (DEC-L029).
            if (cecoId != null) {
                jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?) on conflict do nothing",
                        u.getUserId(), cecoId);
            }
        }
        auditoria.registrar("PERSONAL", personalId, "EDITAR", datos(f.nombre(), f.email(), f.cargo(), f.cecoId()),
                datos(nombre, correo, cargo, cecoId), principal.userId());
        return listar(principal);
    }

    // ---------------------------------------------------------------- inactivar / reactivar

    @Transactional
    public List<PersonalAdminDto> cambiarActivo(UUID personalId, boolean activo, UsuarioPrincipal principal) {
        AdminService.exigirAdmin(principal);
        Ficha f = ficha(personalId);
        if (f.activo() == activo) {
            throw ApiException.badRequest(f.nombre() + (activo ? " ya esta activo." : " ya esta inactivo."));
        }
        if (activo) {
            reactivar(f);
        } else {
            inactivar(f, principal);
        }
        if (f.userId() != null) {
            accesoVigente.invalidar(f.userId());
        }
        Map<String, Object> despues = new HashMap<>();
        despues.put("activo", activo);
        auditoria.registrar("PERSONAL", personalId, activo ? "REACTIVAR" : "INACTIVAR", null, despues,
                principal.userId());
        return listar(principal);
    }

    private void inactivar(Ficha f, UsuarioPrincipal principal) {
        if (principal.userId().equals(f.userId())) {
            throw ApiException.badRequest("No puedes inactivarte a ti mismo.");
        }
        jdbc.update("""
                update personal set active = false, inactivated_at = now(), inactivated_by = ?
                 where personal_id = ?""", principal.userId(), f.id());
        if (f.userId() != null) {
            // La cuenta pierde acceso, salvo que siga activa en el padron de otra empresa.
            jdbc.update("""
                    update usuario set active = false
                     where user_id = ?
                       and not exists (select 1 from personal where user_id = ? and active)""",
                    f.userId(), f.userId());
        }
    }

    private void reactivar(Ficha f) {
        if (!FUENTE_MANUAL.equals(f.fuente()) && !f.inactivadoPorAdmin()) {
            throw ApiException.badRequest(f.nombre() + " ceso en RR.HH.: vuelve a estar activo cuando RR.HH. lo"
                    + " envie en la sincronizacion.");
        }
        jdbc.update("""
                update personal set active = true, inactivated_at = null, inactivated_by = null
                 where personal_id = ?""", f.id());
        if (f.userId() != null) {
            jdbc.update("update usuario set active = true where user_id = ?", f.userId());
        }
    }

    // ---------------------------------------------------------------- utilidades

    private record Ficha(UUID id, UUID companyId, String nombre, String email, String cargo, UUID cecoId,
                         String fuente, boolean activo, boolean inactivadoPorAdmin, UUID userId) {
    }

    private Ficha ficha(UUID personalId) {
        return jdbc.query("""
                select personal_id, company_id, full_name, email, position, ceco_id, fuente, active,
                       inactivated_by is not null, user_id
                  from personal where personal_id = ? for update""",
                (rs, n) -> new Ficha(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getString(7),
                        rs.getBoolean(8), rs.getBoolean(9), rs.getObject(10, UUID.class)),
                personalId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Persona no encontrada."));
    }

    private Empresa empresaManual(String code) {
        String codigo = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (codigo.isEmpty()) {
            throw ApiException.badRequest("Selecciona la empresa.");
        }
        Empresa e = empresaRepository.findByCode(codigo)
                .orElseThrow(() -> ApiException.badRequest("Empresa no encontrada."));
        boolean manual = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from identidad_manual_empresa_habilitada where company_id = ?)",
                Boolean.class, e.getCompanyId()));
        if (!manual) {
            throw ApiException.badRequest(e.getCode() + " se sincroniza desde RR.HH.: su personal se registra alla.");
        }
        return e;
    }

    /** CECO de planilla de la empresa; uno inactivo solo si ya era el suyo. */
    private UUID cecoValido(UUID cecoId, Empresa empresa, UUID actual) {
        if (cecoId == null) {
            return null;
        }
        Ceco c = cecoRepository.findById(cecoId).orElseThrow(() -> ApiException.badRequest("CECO no encontrado."));
        if (!c.getEmpresa().getCompanyId().equals(empresa.getCompanyId())) {
            throw ApiException.badRequest("El CECO no pertenece a la empresa.");
        }
        if (!c.isActive() && !cecoId.equals(actual)) {
            throw ApiException.badRequest("El CECO " + c.getCode() + " esta inactivo.");
        }
        return cecoId;
    }

    /** El correo no puede ser el de la cuenta de otra persona. */
    private void exigirCorreoLibre(String correo, UUID cuentaPropia) {
        if (correo == null) {
            return;
        }
        usuarioRepository.findByCorporateEmailIgnoreCase(correo)
                .filter(otro -> !otro.getUserId().equals(cuentaPropia))
                .ifPresent(otro -> {
                    throw ApiException.badRequest("El correo " + correo + " ya lo usa " + otro.getDisplayName() + ".");
                });
    }

    /** Como en el Excel: mayusculas, sin espacios y con los ceros a la izquierda que se pierden. */
    private static String normalizarDni(String dni) {
        String d = dni == null ? "" : dni.toUpperCase(Locale.ROOT).replace(" ", "");
        return d.matches("\\d{1,7}") ? "0".repeat(8 - d.length()) + d : d;
    }

    private static String correo(String correo) {
        String c = textoOpcional(correo);
        return c != null ? c.toLowerCase(Locale.ROOT) : null;
    }

    private static String textoOpcional(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static Map<String, Object> datos(String nombre, String correo, String cargo, UUID cecoId) {
        Map<String, Object> m = new HashMap<>();
        m.put("nombre", nombre);
        m.put("correo", correo);
        m.put("cargo", cargo);
        m.put("ceco", cecoId != null ? cecoId.toString() : null);
        return m;
    }
}
