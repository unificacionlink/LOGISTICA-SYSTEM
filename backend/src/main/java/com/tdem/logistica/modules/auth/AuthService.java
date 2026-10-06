package com.tdem.logistica.modules.auth;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.repository.UsuarioRepository;
import com.tdem.logistica.repository.UsuarioRolRepository;
import com.tdem.logistica.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            UsuarioRolRepository usuarioRolRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginResult(String token, UsuarioDto usuario, int expirationSeconds) {
    }

    public LoginResult login(String correo, String password) {
        Usuario usuario = usuarioRepository.findByCorporateEmailIgnoreCase(correo)
                .orElseThrow(() -> ApiException.unauthorized("Credenciales invalidas. Verifica tu correo y contrasena."));

        if (!usuario.isActive() || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw ApiException.unauthorized("Credenciales invalidas. Verifica tu correo y contrasena.");
        }

        List<String> roles = usuarioRolRepository.findCodigosRolPorUsuario(usuario.getUserId());
        String token = jwtService.generar(usuario.getUserId(), roles);
        return new LoginResult(token, toDto(usuario, roles), jwtService.expirationSeconds());
    }

    public UsuarioDto me(UUID userId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .filter(Usuario::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Sesion invalida."));
        List<String> roles = usuarioRolRepository.findCodigosRolPorUsuario(userId);
        return toDto(usuario, roles);
    }

    private UsuarioDto toDto(Usuario usuario, List<String> roles) {
        return new UsuarioDto(
                usuario.getUserId(),
                usuario.getDisplayName(),
                usuario.getCorporateEmail(),
                usuario.getEmpresa() != null ? usuario.getEmpresa().getCompanyId() : null,
                usuario.getEmpresa() != null ? usuario.getEmpresa().getCode() : null,
                roles,
                usuario.isMustChangePassword());
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
