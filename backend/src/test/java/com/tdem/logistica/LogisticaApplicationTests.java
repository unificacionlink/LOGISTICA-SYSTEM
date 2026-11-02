package com.tdem.logistica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arranca la aplicacion completa con la configuracion real de application.yml
 * contra {@link BdRrhhSimulada}, conectando como logistica_app: Flyway aplica
 * las migraciones en el esquema "logistica", Hibernate (ddl-auto=validate)
 * comprueba que todas las entidades coinciden con las tablas de ese esquema,
 * y el profile seed-test-data deja los 4 usuarios de prueba listos para login.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class LogisticaApplicationTests {

	private static EmbeddedPostgres pg;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@DynamicPropertySource
	static void bd(DynamicPropertyRegistry registry) throws IOException, SQLException {
		pg = BdRrhhSimulada.iniciar();
		registry.add("spring.datasource.url", () -> BdRrhhSimulada.jdbcUrl(pg));
		registry.add("spring.datasource.username", () -> BdRrhhSimulada.USUARIO_APP);
		registry.add("spring.datasource.password", () -> BdRrhhSimulada.PASSWORD_APP);
		registry.add("app.jwt.secret", () -> "clave-de-prueba-solo-para-tests-0123456789-abcdefghijklmnop");
	}

	@AfterAll
	static void detener() throws IOException {
		if (pg != null) {
			pg.close();
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	void seedTestDataCreaLosUsuariosConRolYPassword() {
		List<String> roles = jdbc.queryForList("""
				select r.code from usuario u
				join usuario_rol ur on ur.user_id = u.user_id join rol r on r.role_id = ur.role_id
				where u.corporate_email like '%@logistica.test' order by r.code""", String.class);
		assertEquals(List.of("ADMIN", "APROBADOR", "GERENCIA", "LOGISTICA", "PYC", "SOLICITANTE"), roles);

		String hash = jdbc.queryForObject(
				"select password_hash from usuario where corporate_email = 'logistica@logistica.test'", String.class);
		assertTrue(passwordEncoder.matches("Logistica2026!", hash));
	}

}
