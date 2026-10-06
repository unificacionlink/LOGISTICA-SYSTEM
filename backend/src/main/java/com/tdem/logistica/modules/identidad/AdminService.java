package com.tdem.logistica.modules.identidad;

import com.tdem.logistica.common.ApiException;
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

    public AdminService(UsuarioRepository usuarioRepository, UsuarioRolRepository usuarioRolRepository,
                        RolRepository rolRepository, EmpresaRepository empresaRepository, CecoRepository cecoRepository,
                        PersonalSyncService syncService, PasswordEncoder passwordEncoder, AccesoVigente accesoVigente,
                        JdbcTemplate jdbc) {
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
                .map(c -> new CecoAdminDto(c.getCecoId(), c.getEmpresa().getCode(), c.getCode(), c.getName(), c.isActive()))
                .toList();
        Timestamp ultima = jdbc.queryForObject(
                "select max(source_synced_at) from usuario where fuente = 'RRHH_SYNC'", Timestamp.class);
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
        String codigo = r.code().trim().toUpperCase(Locale.ROOT);
        if (cecoRepository.findByEmpresa_CompanyIdAndCode(empresa.getCompanyId(), codigo).isPresent()) {
            throw ApiException.badRequest("Ya existe el CECO " + codigo + " en " + empresa.getCode() + ".");
        }
        Ceco c = new Ceco();
        c.setCecoId(UUID.randomUUID());
        c.setEmpresa(empresa);
        c.setCode(codigo);
        c.setName(r.nombre().trim());
        c.setActive(true);
        cecoRepository.save(c);
        return datos(principal);
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
        List<String> orden = List.of(Roles.SOLICITANTE, Roles.APROBADOR, Roles.LOGISTICA, Roles.ADMIN);
        return roles.stream().sorted(Comparator.comparingInt(r -> {
            int i = orden.indexOf(r);
            return i < 0 ? orden.size() : i;
        })).toList();
    }
}
