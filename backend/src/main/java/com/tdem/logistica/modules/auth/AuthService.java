package com.tdem.logistica.modules.auth;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.repository.UsuarioRolRepository;
import com.tdem.logistica.security.AccesoVigente;
import com.tdem.logistica.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final AccesoVigente accesoVigente;
    private final com.tdem.logistica.realtime.RealtimeNotifier realtime;

    private static final Pattern CODIGO_TUTORIAL = Pattern.compile("^[a-z-]{1,40}$");

    public AuthService(
            UsuarioRepository usuarioRepository,
            UsuarioRolRepository usuarioRolRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JdbcTemplate jdbc,
            AccesoVigente accesoVigente,
            com.tdem.logistica.realtime.RealtimeNotifier realtime) {
        this.realtime = realtime;
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.accesoVigente = accesoVigente;
    }

    public record LoginResult(String token, UsuarioDto usuario, int expirationSeconds) {
    }

    /**
     * DEC-L090: cada ingreso abre una sesion nueva y deja invalidas las anteriores
     * del mismo usuario (otro dispositivo o navegador); la que estaba abierta se
     * entera al instante por el aviso en vivo o en su siguiente pedido.
     */
    @Transactional
    public LoginResult login(String correo, String password) {
        Usuario usuario = usuarioRepository.findByCorporateEmailIgnoreCase(correo)
                .orElseThrow(() -> ApiException.unauthorized("Credenciales invalidas. Verifica tu correo y contrasena."));

        if (!usuario.isActive() || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciales invalidas. Verifica tu correo y contrasena.");
        }

        List<String> roles = usuarioRolRepository.findCodigosRolPorUsuario(usuario.getUserId());
        UUID sesion = UUID.randomUUID();
        jdbc.update("update usuario set session_id = ? where user_id = ?", sesion, usuario.getUserId());
        accesoVigente.invalidar(usuario.getUserId());
        realtime.sesionIniciada(usuario.getUserId(), sesion);
        String token = jwtService.generar(usuario.getUserId(), roles, sesion);
        return new LoginResult(token, toDto(usuario, roles, sesion), jwtService.expirationSeconds());
    }

    /** DEC-L090: cerrar sesion rota la sesion vigente a una que ningun token tiene. */
    @Transactional
    public void cerrarSesion(UUID userId) {
        jdbc.update("update usuario set session_id = ? where user_id = ?", UUID.randomUUID(), userId);
        accesoVigente.invalidar(userId);
    }

    /**
     * Corre cada vez que se abre la app: una sola consulta (usuario, empresa y
     * recorridos vistos), los roles vigentes de {@link AccesoVigente} (ya en
     * memoria por el filtro JWT) y sin transaccion (sin ida y vuelta de COMMIT).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UsuarioDto me(UUID userId) {
        AccesoVigente.Acceso acceso = accesoVigente.obtener(userId);
        List<UsuarioDto> filas = jdbc.query("""
                select u.user_id, u.display_name, u.corporate_email, e.company_id, e.code, u.must_change_password,
                       array(select t.tutorial_code from usuario_tutorial t
                              where t.user_id = u.user_id order by t.tutorial_code) tutoriales
                  from usuario u left join empresa e on e.company_id = u.company_id
                 where u.user_id = ? and u.active""",
                (rs, i) -> new UsuarioDto(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5),
                        acceso.roles(),
                        rs.getBoolean(6),
                        List.of((String[]) rs.getArray(7).getArray()),
                        acceso.sesion()),
                userId);
        if (filas.isEmpty() || !acceso.activo()) {
            throw ApiException.unauthorized("Sesion invalida.");
        }
        return filas.get(0);
    }

    private UsuarioDto toDto(Usuario usuario, List<String> roles) {
        return toDto(usuario, roles, accesoVigente.obtener(usuario.getUserId()).sesion());
    }

    private UsuarioDto toDto(Usuario usuario, List<String> roles, UUID sesion) {
        return new UsuarioDto(
                usuario.getUserId(),
                usuario.getDisplayName(),
                usuario.getCorporateEmail(),
                usuario.getEmpresa() != null ? usuario.getEmpresa().getCompanyId() : null,
                usuario.getEmpresa() != null ? usuario.getEmpresa().getCode() : null,
                roles,
                usuario.isMustChangePassword(),
                tutorialesVistos(usuario.getUserId()),
                sesion);
    }

    private List<String> tutorialesVistos(UUID userId) {
        return jdbc.queryForList(
                "select tutorial_code from usuario_tutorial where user_id = ? order by tutorial_code",
                String.class, userId);
    }

    /**
     * Marca un recorrido guiado como visto o saltado por el propio usuario
     * (DEC-L055); repetirlo desde Ayuda solo actualiza la fecha.
     */
    @Transactional
    public UsuarioDto marcarTutorial(UUID userId, String codigo, String estado) {
        if (codigo == null || !CODIGO_TUTORIAL.matcher(codigo).matches()) {
            throw ApiException.badRequest("Recorrido no valido.");
        }
        jdbc.update("""
                insert into usuario_tutorial (user_id, tutorial_code, status) values (?, ?, ?)
                on conflict (user_id, tutorial_code)
                do update set status = excluded.status, updated_at = now()""", userId, codigo, estado);
        return me(userId);
    }

    /**
     * Cambio de la propia contrasena. Obligatorio tras una contrasena temporal
     * generada por ADMIN (DEC-L024).
     */
    @Transactional
    public UsuarioDto cambiarPassword(UUID userId, String actual, String nueva) {
        Usuario usuario = usuarioRepository.findById(userId)
                .filter(Usuario::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        if (usuario.getPasswordHash() == null || !passwordEncoder.matches(actual, usuario.getPasswordHash())) {
            throw ApiException.badRequest("La contrasena actual no es correcta.");
        }
        validarNueva(nueva);
        if (passwordEncoder.matches(nueva, usuario.getPasswordHash())) {
            throw ApiException.badRequest("La nueva contrasena debe ser distinta de la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(nueva));
        usuario.setMustChangePassword(false);
        usuarioRepository.save(usuario);
        return toDto(usuario, usuarioRolRepository.findCodigosRolPorUsuario(userId));
    }

    static void validarNueva(String nueva) {
        if (nueva == null || nueva.length() < 8) {
            throw ApiException.badRequest("La nueva contrasena debe tener al menos 8 caracteres.");
        }
        if (!nueva.chars().anyMatch(Character::isLetter) || !nueva.chars().anyMatch(Character::isDigit)) {
            throw ApiException.badRequest("La nueva contrasena debe combinar letras y numeros.");
        }
    }
}
