package com.tdem.logistica;

import com.tdem.logistica.entity.Usuario;
import com.tdem.logistica.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Crea (si no existen) los 4 usuarios de prueba, uno por rol
 * (solicitante/logistica/aprobador/admin @logistica.test, empresa TDEMSRL; el
 * solicitante con todos los CECOs de TDEMSRL asignados), y
 * les asigna/renueva password_hash. Antes existian solo en la BD vieja
 * (sembrados a mano); desde DEC-L016 el esquema "logistica" nace vacio de
 * usuarios. Se activa solo con el profile "seed-test-data":
 *   mvn spring-boot:run -Dspring-boot.run.profiles=local,seed-test-data
 */
@Component
@Profile("seed-test-data")
public class TestDataSeeder implements CommandLineRunner {

    private static final String PASSWORD = "Logistica2026!";
    // correo -> [nombre visible, codigo de rol]
    private static final Map<String, String[]> USUARIOS = Map.of(
            "solicitante@logistica.test", new String[]{"Solicitante de Prueba", "SOLICITANTE"},
            "logistica@logistica.test", new String[]{"Logistica de Prueba", "LOGISTICA"},
            "aprobador@logistica.test", new String[]{"Aprobador de Prueba", "APROBADOR"},
            "admin@logistica.test", new String[]{"Admin de Prueba", "ADMIN"});

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public TestDataSeeder(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String hash = passwordEncoder.encode(PASSWORD);
        USUARIOS.forEach((correo, datos) -> {
            jdbc.update("""
                    insert into usuario (corporate_email, display_name, company_id, fuente)
                    select ?, ?, company_id, 'MANUAL' from empresa where code = 'TDEMSRL'
                    on conflict (corporate_email) do nothing""", correo, datos[0]);
            jdbc.update("""
                    insert into usuario_rol (user_id, role_id, company_id)
                    select u.user_id, r.role_id, u.company_id
                    from usuario u join rol r on r.code = ?
                    where u.corporate_email = ?
                    on conflict do nothing""", datos[1], correo);
            usuarioRepository.findByCorporateEmailIgnoreCase(correo).ifPresent((Usuario u) -> {
                u.setPasswordHash(hash);
                usuarioRepository.save(u);
                System.out.println("[seed-test-data] listo: " + correo + " (" + datos[1] + ")");
            });
        });
        // El solicitante solo pide para sus CECOs asignados (DEC-L029): se le dan los de TDEMSRL.
        jdbc.update("""
                insert into usuario_ceco (user_id, ceco_id)
                select u.user_id, c.ceco_id
                from usuario u join ceco c on c.company_id = u.company_id and c.active
                where u.corporate_email = 'solicitante@logistica.test'
                on conflict do nothing""");
        System.out.println("[seed-test-data] Password para todos: " + PASSWORD);
    }
}
