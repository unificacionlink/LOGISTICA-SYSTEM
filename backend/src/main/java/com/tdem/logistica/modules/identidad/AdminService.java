package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.common.Auditoria;
import com.tdem.logistica.common.CodigoCeco;
import com.tdem.logistica.common.AppConstants.Roles;
import com.tdem.logistica.entity.Ceco;
import com.tdem.logistica.entity.Empresa;
import com.tdem.logistica.entity.Rol;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.modules.identidad.dto.*;
import com.tdem.logistica.repository.CecoRepository;
import com.tdem.logistica.repository.EmpresaRepository;
import com.tdem.logistica.repository.RolRepository;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.repository.UsuarioRolRepository;
import com.tdem.logistica.security.AccesoVigente;
import com.tdem.logistica.security.UsuarioPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Administracion de identidad (solo ADMIN, DEC-L024). La identidad de las
 * empresas que cubre RRHH-SYSTEM la mantiene {@link PersonalSyncService};
 * aqui ADMIN asigna roles, da acceso (contrasena temporal) y, solo para
 * empresas sin padron en RRHH ({@code identidad_manual_empresa_habilitada}),
 * da de alta usuarios y CECOs a mano (DEC-L002).
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final String FUENTE_MANUAL = "MANUAL";
    // Sin caracteres ambiguos (0/O, 1/l/I) para dictarla sin errores.
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final EmpresaRepository empresaRepository;
    private final CecoRepository cecoRepository;
    private final PersonalSyncService syncService;
    private final PasswordEncoder passwordEncoder;
    private final AccesoVigente accesoVigente;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public AdminService(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository,
                        RolRepository rolRepository, EmpresaRepository empresaRepository, CecoRepository cecoRepository,
                        PersonalSyncService syncService, PasswordEncoder passwordEncoder, AccesoVigente accesoVigente,
                        JdbcTemplate jdbc, Auditoria auditoria) {
        this.auditoria = auditoria;
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.rolRepository = rolRepository;
        this.empresaRepository = empresaRepository;
        this.cecoRepository = cecoRepository;
        this.syncService = syncService;
        this.passwordEncoder = passwordEncoder;
        this.accesoVigente = accesoVigente;
        this.jdbc = jdbc;
    }

    static void exigirAdmin(UsuarioPrincipal principal) {
        if (!principal.tieneAlgunRol(Roles.ADMIN)) {
            throw ApiException.forbidden("Solo un administrador puede gestionar usuarios.");
        }
    }

    // ---------------------------------------------------------------- lectura

    public AdminDatosDto datos(UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Map<UUID, List<String>> roles = new HashMap<>();
        for (Object[] fila : usuarioRolRepository.findCodigosRolTodos()) {
            roles.computeIfAbsent((UUID) fila[0], k -> new ArrayList<>()).add((String) fila[1]);
        }
        Map<UUID, List<UUID>> cecos = new HashMap<>();
        jdbc.query("select user_id, ceco_id from usuario_ceco", rs -> {
            cecos.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
        });
        Set<UUID> manuales = empresasManuales();

        List<UsuarioAdminDto> usuarios = usuarioRepository.findAllConEmpresa().stream()
                .map(u -> new UsuarioAdminDto(u.getUserId(), u.getDisplayName(), u.getCorporateEmail(),
                        u.getEmployeeExternalId(), u.getEmpresa() != null ? u.getEmpresa().getCode() : null,
                        u.getCargo(), ordenarRoles(roles.getOrDefault(u.getUserId(), List.of())),
                        cecos.getOrDefault(u.getUserId(), List.of()), u.getFuente(), u.isActive(),
                        u.getSourceSyncedAt(), u.getPasswordHash() != null, u.isMustChangePassword()))
                .toList();
        List<EmpresaAdminDto> empresas = empresaRepository.findAllByActiveTrueOrderByCode().stream()
                .map(e -> new EmpresaAdminDto(e.getCompanyId(), e.getCode(), e.getLegalName(), manuales.contains(e.getCompanyId())))
                .toList();
        List<CecoAdminDto> cecosDto = cecoRepository.findAllConEmpresa().stream()
                .map(c -> new CecoAdminDto(c.getCecoId(), c.getEmpresa().getCode(), c.getCode(), c.getName(), c.isActive(),
                        c.getApproverUserId()))
                .toList();
        Timestamp ultima = jdbc.queryForObject(
                "select max(source_synced_at) from personal where fuente = 'RRHH_SYNC'", Timestamp.class);
        return new AdminDatosDto(usuarios, empresas, cecosDto, ordenarRoles(rolesActivos().keySet()),
                syncService.configurado(), ultima != null ? ultima.toInstant() : null, syncService.ultimoResultado());
    }

    // ---------------------------------------------------------------- escritura

    @Transactional
    public AdminDatosDto asignarRoles(UUID userId, List<String> codigos, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Usuario u = usuario(userId);
        if (u.getEmpresa() == null) {
            throw ApiException.badRequest("El usuario no tiene empresa asignada.");
        }
        Map<String, Rol> disponibles = rolesActivos();
        Set<String> nuevos = new LinkedHashSet<>(codigos);
        for (String c : nuevos) {
            if (!disponibles.containsKey(c)) {
                throw ApiException.badRequest("Rol invalido: " + c + ".");
            }
        }
        if (userId.equals(principal.userId()) && !nuevos.contains(Roles.ADMIN)) {
            throw ApiException.badRequest("No puedes quitarte el rol de administrador a ti mismo.");
        }
        jdbc.update("delete from usuario_rol where user_id = ?", userId);
        for (String c : nuevos) {
            jdbc.update("insert into usuario_rol (user_id, role_id, company_id) values (?, ?, ?)",
                    userId, disponibles.get(c).getRoleId(), u.getEmpresa().getCompanyId());
        }
        accesoVigente.invalidar(userId);
        return datos(principal);
    }

    /** Genera una contrasena temporal (se muestra una sola vez) y obliga a cambiarla al ingresar. */
    @Transactional
    public PasswordTemporalDto passwordTemporal(UUID userId, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Usuario u = usuario(userId);
        if (!u.isActive()) {
            throw ApiException.badRequest("El usuario esta inactivo.");
        }
        return nuevaPassword(u);
    }

    private PasswordTemporalDto nuevaPassword(Usuario u) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO[RANDOM.nextInt(ALFABETO.length)]);
        }
        // Garantiza letras y digitos (misma regla que el cambio de contrasena).
        sb.setCharAt(RANDOM.nextInt(5), (char) ('2' + RANDOM.nextInt(8)));
        sb.setCharAt(5 + RANDOM.nextInt(5), (char) ('a' + RANDOM.nextInt(8)));
        String password = sb.toString();
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMustChangePassword(true);
        usuarioRepository.save(u);
        return new PasswordTemporalDto(u.getCorporateEmail(), password);
    }

    /** Solo usuarios MANUAL: los de RRHH se activan/desactivan con el sync. */
    @Transactional
    public AdminDatosDto cambiarActivo(UUID userId, boolean activo, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Usuario u = usuario(userId);
        if (!FUENTE_MANUAL.equals(u.getFuente())) {
            throw ApiException.badRequest("Este usuario viene de RR.HH.: su estado lo controla la sincronizacion. "
                    + "Para quitarle acceso, quitale los roles.");
        }
        if (userId.equals(principal.userId()) && !activo) {
            throw ApiException.badRequest("No puedes desactivarte a ti mismo.");
        }
        u.setActive(activo);
        usuarioRepository.save(u);
        accesoVigente.invalidar(userId);
        return datos(principal);
    }

    @Transactional
    public AdminDatosDto crearUsuario(CrearUsuarioRequest r, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Empresa empresa = empresaManual(r.empresaCode());
        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.findByCorporateEmailIgnoreCase(correo).isPresent()) {
            throw ApiException.badRequest("Ya existe un usuario con el correo " + correo + ".");
        }
        String dni = r.dni() != null && !r.dni().isBlank() ? r.dni().trim() : null;
        if (dni != null && usuarioRepository.findByEmployeeExternalId(dni).isPresent()) {
            throw ApiException.badRequest("Ya existe un usuario con el DNI " + dni + ".");
        }
        Ceco ceco = null;
        if (r.cecoId() != null) {
            ceco = cecoRepository.findById(r.cecoId())
                    .filter(c -> c.getEmpresa().getCompanyId().equals(empresa.getCompanyId()))
                    .orElseThrow(() -> ApiException.badRequest("El CECO no pertenece a la empresa."));
        }
        Map<String, Rol> disponibles = rolesActivos();
        for (String c : r.roles()) {
            if (!disponibles.containsKey(c)) {
                throw ApiException.badRequest("Rol invalido: " + c + ".");
            }
        }
        Usuario u = new Usuario();
        u.setUserId(UUID.randomUUID());
        u.setCorporateEmail(correo);
        u.setDisplayName(r.nombre().trim());
        u.setEmployeeExternalId(dni);
        u.setCargo(r.cargo() != null && !r.cargo().isBlank() ? r.cargo().trim() : null);
        u.setEmpresa(empresa);
        u.setFuente(FUENTE_MANUAL);
        u.setActive(true);
        usuarioRepository.saveAndFlush(u);
        for (String c : new LinkedHashSet<>(r.roles())) {
            jdbc.update("insert into usuario_rol (user_id, role_id, company_id) values (?, ?, ?)",
                    u.getUserId(), disponibles.get(c).getRoleId(), empresa.getCompanyId());
        }
        if (ceco != null) {
            jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?)", u.getUserId(), ceco.getCecoId());
        }
        if (dni != null) {
            // DEC-L041: la persona tambien queda en el padron de su empresa, vinculada a la cuenta.
            jdbc.update("""
                    insert into personal (company_id, dni, full_name, email, position, ceco_id, fuente, user_id)
                    values (?, ?, ?, ?, ?, ?, 'MANUAL', ?)
                    on conflict (company_id, dni) do update set user_id = excluded.user_id,
                           email = coalesce(personal.email, excluded.email)""",
                    empresa.getCompanyId(), dni, u.getDisplayName(), correo, u.getCargo(),
                    ceco != null ? ceco.getCecoId() : null, u.getUserId());
        }
        return datos(principal);
    }

    /** Datos de un usuario MANUAL (DEC-L028). Los de RR.HH. los mantiene el sync. */
    @Transactional
    public AdminDatosDto editarUsuario(UUID userId, EditarUsuarioRequest r, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Usuario u = usuario(userId);
        if (!FUENTE_MANUAL.equals(u.getFuente())) {
            throw ApiException.badRequest("Este usuario viene de RR.HH.: sus datos se corrigen alla.");
        }
        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        usuarioRepository.findByCorporateEmailIgnoreCase(correo)
                .filter(otro -> !otro.getUserId().equals(userId))
                .ifPresent(otro -> {
                    throw ApiException.badRequest("Ya existe un usuario con el correo " + correo + ".");
                });
        String dni = r.dni() != null && !r.dni().isBlank() ? r.dni().trim().toUpperCase(Locale.ROOT) : null;
        if (dni != null) {
            usuarioRepository.findByEmployeeExternalId(dni)
                    .filter(otro -> !otro.getUserId().equals(userId))
                    .ifPresent(otro -> {
                        throw ApiException.badRequest("Ya existe un usuario con el DNI " + dni + ".");
                    });
        }
        u.setDisplayName(r.nombre().trim());
        u.setCorporateEmail(correo);
        u.setEmployeeExternalId(dni);
        u.setCargo(r.cargo() != null && !r.cargo().isBlank() ? r.cargo().trim() : null);
        usuarioRepository.saveAndFlush(u);
        // Su ficha del padron (DEC-L041) muestra los mismos datos.
        jdbc.update("update personal set full_name = ?, position = ?, email = ? where user_id = ? and fuente = 'MANUAL'",
                u.getDisplayName(), u.getCargo(), correo, userId);
        return datos(principal);
    }

    /**
     * CECOs para los que el usuario puede pedir (DEC-L029), de cualquier empresa y para
     * cualquier usuario: es autorizacion de Logistica, no dato de personal. El sync de RR.HH.
     * y la carga Excel solo agregan el CECO de planilla, nunca quitan los asignados aqui.
     */
    @Transactional
    public AdminDatosDto asignarCecos(UUID userId, List<UUID> cecoIds, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        usuario(userId);
        Set<UUID> nuevos = new LinkedHashSet<>(cecoIds);
        Set<UUID> actuales = new HashSet<>(cecoRepository.findIdsAsignados(userId));
        Map<UUID, Ceco> existentes = cecoRepository.findAllById(nuevos).stream()
                .collect(Collectors.toMap(Ceco::getCecoId, c -> c));
        for (UUID id : nuevos) {
            Ceco c = existentes.get(id);
            if (c == null) {
                throw ApiException.badRequest("CECO no encontrado.");
            }
            // Uno ya asignado que luego se desactivo puede quedarse; no se asignan inactivos nuevos.
            if (!c.isActive() && !actuales.contains(id)) {
                throw ApiException.badRequest("El CECO " + c.getCode() + " esta inactivo.");
            }
        }
        jdbc.update("delete from usuario_ceco where user_id = ?", userId);
        for (UUID id : nuevos) {
            jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?)", userId, id);
        }
        return datos(principal);
    }

    /** CECOs a mano solo para empresas sin padron en RRHH; los demas llegan con el sync. */
    @Transactional
    public AdminDatosDto crearCeco(CrearCecoRequest r, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Empresa empresa = empresaManual(r.empresaCode());
        String codigo = CodigoCeco.normalizar(r.code()); // DEC-L032
        if (cecoRepository.findByEmpresa_CompanyIdAndCode(empresa.getCompanyId(), codigo).isPresent()) {
            throw ApiException.badRequest("Ya existe el CECO " + codigo + " en " + empresa.getCode() + ".");
        }
        Ceco c = new Ceco();
        c.setCecoId(UUID.randomUUID());
        c.setEmpresa(empresa);
        c.setCode(codigo);
        c.setName(CodigoCeco.nombre(r.nombre())); // DEC-L033
        c.setActive(true);
        cecoRepository.save(c);
        return datos(principal);
    }

    /**
     * DEC-L030/DEC-L037: CECOs que aprueba el usuario (APROBADOR o GERENCIA,
     * activo), como en Accesos / usuarios de la V38.7. El dato sigue en el CECO
     * (un aprobador por CECO): elegir un CECO que tenia otro aprobador se lo
     * reasigna; quitarlo lo deja sin aprobador. Lista vacia = no aprueba ninguno.
     */
    @Transactional
    public AdminDatosDto asignarCecosAprobados(UUID userId, List<UUID> cecoIds, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Usuario u = usuario(userId);
        Set<UUID> nuevos = new LinkedHashSet<>(cecoIds);
        if (!nuevos.isEmpty()) {
            if (!u.isActive()) {
                throw ApiException.badRequest("El aprobador debe estar activo.");
            }
            List<String> roles = usuarioRolRepository.findCodigosRolPorUsuario(userId);
            if (!roles.contains(Roles.APROBADOR) && !roles.contains(Roles.GERENCIA)) {
                throw ApiException.badRequest(u.getDisplayName() + " no tiene rol Aprobador ni Gerencia.");
            }
        }
        // Por JDBC y no por la entidad: datos() relee los CECO en esta misma
        // transaccion y una entidad ya cargada devolveria el aprobador viejo.
        Set<UUID> actuales = new HashSet<>(jdbc.queryForList(
                "select ceco_id from ceco where approver_user_id = ?", UUID.class, userId));
        List<UUID> agregados = nuevos.stream().filter(c -> !actuales.contains(c)).toList();
        if (!agregados.isEmpty()) {
            // Estado de todos los CECO nuevos en una consulta (antes una por CECO).
            Map<UUID, Boolean> activos = new HashMap<>();
            jdbc.query("select ceco_id, active from ceco where ceco_id in ("
                            + String.join(",", Collections.nCopies(agregados.size(), "?")) + ")",
                    rs -> {
                        activos.put(rs.getObject(1, UUID.class), rs.getBoolean(2));
                    }, agregados.toArray());
            for (UUID cecoId : agregados) {
                Boolean activo = activos.get(cecoId);
                if (activo == null) {
                    throw ApiException.badRequest("CECO no encontrado.");
                }
                if (!activo) {
                    throw ApiException.badRequest("No se asignan CECOs inactivos.");
                }
            }
            for (UUID cecoId : agregados) {
                aplicarAprobador(cecoId, userId, principal);
            }
        }
        for (UUID cecoId : actuales) {
            if (!nuevos.contains(cecoId)) {
                aplicarAprobador(cecoId, null, principal);
            }
        }
        return datos(principal);
    }

    /**
     * Fija el aprobador de un CECO. Los REQ del CECO que siguen pendientes pasan
     * al nuevo aprobador (asi un reemplazo por vacaciones no deja la bandeja
     * trabada); si el nuevo es el propio solicitante, ese REQ queda sin asignar.
     * Se audita.
     */
    private void aplicarAprobador(UUID cecoId, UUID aprobadorId, UsuarioPrincipal principal) {
        UUID anterior = jdbc.query("select approver_user_id from ceco where ceco_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), cecoId).getFirst();
        jdbc.update("update ceco set approver_user_id = ?, approver_updated_by = ?, approver_updated_at = now() where ceco_id = ?",
                aprobadorId, principal.userId(), cecoId);
        jdbc.update("""
                update aprobacion a
                   set assigned_to = case when r.requester_user_id = cast(? as uuid) then null else cast(? as uuid) end
                  from requerimiento r
                 where a.entity_type = 'REQUERIMIENTO' and a.status = 'PENDIENTE'
                   and a.entity_id = r.request_id and r.ceco_id = ?""",
                aprobadorId, aprobadorId, cecoId);
        Map<String, Object> antes = new HashMap<>();
        antes.put("aprobador", anterior);
        Map<String, Object> despues = new HashMap<>();
        despues.put("aprobador", aprobadorId);
        auditoria.registrar("CECO", cecoId, "ASIGNAR_APROBADOR", antes, despues, principal.userId());
    }

    // ---------------------------------------------------------------- padron (DEC-L041)

    /** Personal vigente de todas las empresas; la pantalla lo muestra por empresa, sin mezclar. */
    public List<PersonalAdminDto> personal(UsuarioPrincipal principal) {
        exigirAdmin(principal);
        return jdbc.query("""
                select p.personal_id, e.code, p.dni, p.full_name, coalesce(u.corporate_email, p.email),
                       p.position, c.code, c.name, p.fuente, p.user_id
                  from personal p
                  join empresa e on e.company_id = p.company_id
                  left join ceco c on c.ceco_id = p.ceco_id
                  left join usuario u on u.user_id = p.user_id
                 where p.active
                 order by e.code, p.full_name""",
                (rs, n) -> new PersonalAdminDto(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getObject(10, UUID.class)));
    }

    /** Persona del padron, leida para dar acceso. */
    private record Persona(UUID companyId, String dni, String nombre, String cargo, UUID cecoId, String fuente,
                           boolean activo, UUID userId) {
    }

    /**
     * Crea la cuenta de una persona del padron (DEC-L041): correo de ingreso, roles
     * y su CECO de planilla; devuelve la contrasena temporal. La cuenta hereda la
     * fuente del padron: la de RR.HH. la sigue controlando el sync. Si ya habia
     * una cuenta con su DNI (p. ej. de otra empresa), solo se vincula.
     */
    @Transactional
    public DarAccesoDto darAcceso(UUID personalId, DarAccesoRequest r, UsuarioPrincipal principal) {
        exigirAdmin(principal);
        Persona p = jdbc.query("""
                select company_id, dni, full_name, position, ceco_id, fuente, active, user_id
                  from personal where personal_id = ? for update""",
                (rs, n) -> new Persona(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getString(6), rs.getBoolean(7), rs.getObject(8, UUID.class)),
                personalId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Persona no encontrada."));
        if (!p.activo()) {
            throw ApiException.badRequest(p.nombre() + " ya no esta activo en el padron.");
        }
        if (p.userId() != null) {
            throw ApiException.badRequest(p.nombre() + " ya tiene cuenta.");
        }
        Map<String, Rol> disponibles = rolesActivos();
        for (String c : r.roles()) {
            if (!disponibles.containsKey(c)) {
                throw ApiException.badRequest("Rol invalido: " + c + ".");
            }
        }
        String correo = r.correo().trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> porDni = usuarioRepository.findByEmployeeExternalId(p.dni());
        if (porDni.isPresent()) {
            // Ya tenia cuenta (de otra empresa o anterior al padron): solo se vincula.
            jdbc.update("update personal set user_id = ? where personal_id = ?", porDni.get().getUserId(), personalId);
            return new DarAccesoDto(personal(principal), null);
        }
        usuarioRepository.findByCorporateEmailIgnoreCase(correo).ifPresent(otro -> {
            throw ApiException.badRequest("El correo " + correo + " ya lo usa " + otro.getDisplayName() + ".");
        });

        Usuario u = new Usuario();
        u.setUserId(UUID.randomUUID());
        u.setCorporateEmail(correo);
        u.setDisplayName(p.nombre());
        u.setEmployeeExternalId(p.dni());
        u.setCargo(p.cargo());
        u.setEmpresa(empresaRepository.getReferenceById(p.companyId()));
        u.setFuente(p.fuente());
        u.setActive(true);
        u.setSourceSyncedAt(FUENTE_MANUAL.equals(p.fuente()) ? null : Instant.now());
        usuarioRepository.saveAndFlush(u);
        for (String c : new LinkedHashSet<>(r.roles())) {
            jdbc.update("insert into usuario_rol (user_id, role_id, company_id) values (?, ?, ?)",
                    u.getUserId(), disponibles.get(c).getRoleId(), p.companyId());
        }
        if (p.cecoId() != null) {
            jdbc.update("insert into usuario_ceco (user_id, ceco_id) values (?, ?) on conflict do nothing",
                    u.getUserId(), p.cecoId());
        }
        // El padron manual guarda el correo; el de RR.HH. lo sigue reflejando el sync.
        jdbc.update("""
                update personal set user_id = ?,
                       email = case when fuente = 'MANUAL' then ? else email end
                 where personal_id = ?""", u.getUserId(), correo, personalId);
        PasswordTemporalDto acceso = nuevaPassword(u);

        Map<String, Object> despues = new HashMap<>();
        despues.put("usuario", u.getUserId());
        despues.put("correo", correo);
        despues.put("roles", new ArrayList<>(new LinkedHashSet<>(r.roles())));
        auditoria.registrar("PERSONAL", personalId, "DAR_ACCESO", null, despues, principal.userId());
        accesoVigente.invalidar(u.getUserId());
        return new DarAccesoDto(personal(principal), acceso);
    }

    /** Sin transaccion propia: la llamada a RRHH no debe retener una conexion; el sync abre la suya. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public AdminDatosDto sincronizar(UsuarioPrincipal principal) {
        exigirAdmin(principal);
        syncService.sincronizar();
        accesoVigente.invalidarTodo();
        return datos(principal);
    }

    // ---------------------------------------------------------------- utilidades

    private Usuario usuario(UUID id) {
        return usuarioRepository.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
    }

    private Map<String, Rol> rolesActivos() {
        return rolRepository.findAll().stream().filter(Rol::isActive)
                .collect(Collectors.toMap(Rol::getCode, r -> r));
    }

    private Set<UUID> empresasManuales() {
        return new HashSet<>(jdbc.queryForList(
                "select company_id from identidad_manual_empresa_habilitada", UUID.class));
    }

    private Empresa empresaManual(String code) {
        Empresa e = empresaRepository.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("Empresa no encontrada."));
        if (!empresasManuales().contains(e.getCompanyId())) {
            throw ApiException.badRequest(e.getCode() + " se sincroniza desde RR.HH.: no admite altas manuales.");
        }
        return e;
    }

    /** Orden fijo de roles, del menor al mayor alcance. */
    private static List<String> ordenarRoles(Collection<String> roles) {
        List<String> orden = List.of(Roles.SOLICITANTE, Roles.APROBADOR, Roles.GERENCIA, Roles.PYC, Roles.LOGISTICA, Roles.ADMIN);
        return roles.stream().sorted(Comparator.comparingInt(r -> {
            int i = orden.indexOf(r);
            return i < 0 ? orden.size() : i;
        })).toList();
    }
}
