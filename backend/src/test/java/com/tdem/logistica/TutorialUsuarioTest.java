package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.auth.AuthService;
import com.tdem.logistica.modules.auth.UsuarioDto;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEC-L055: el recorrido guiado se ofrece en el primer ingreso; lo visto o
 * saltado se guarda por usuario y vuelve en la sesion (`me`).
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class TutorialUsuarioTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AuthService auth;
	@MockitoBean private StorageClient storage;

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
	void loVistoQuedaPorUsuarioYVuelveEnLaSesion() {
		UUID solicitante = usuario("solicitante@logistica.test");
		UUID logistica = usuario("logistica@logistica.test");

		assertEquals(List.of(), auth.me(solicitante).tutorialesVistos(), "un usuario nuevo no vio ninguno");

		UsuarioDto tras = auth.marcarTutorial(solicitante, "solicitante", "OMITIDO");
		assertEquals(List.of("solicitante"), tras.tutorialesVistos());
		assertEquals(List.of(), auth.me(logistica).tutorialesVistos(), "no se mezcla con otro usuario");

		// Repetirlo desde Ayuda y terminarlo actualiza el estado, sin duplicar.
		auth.marcarTutorial(solicitante, "solicitante", "COMPLETADO");
		assertEquals(List.of("solicitante"), auth.me(solicitante).tutorialesVistos());
		assertEquals("COMPLETADO", jdbc.queryForObject(
				"select status from usuario_tutorial where user_id = ? and tutorial_code = 'solicitante'",
				String.class, solicitante));

		auth.marcarTutorial(solicitante, "logistica", "COMPLETADO");
		assertEquals(List.of("logistica", "solicitante"), auth.me(solicitante).tutorialesVistos());
	}

	@Test
	void codigoNoValidoSeRechaza() {
		UUID solicitante = usuario("solicitante@logistica.test");
		assertThrows(ApiException.class, () -> auth.marcarTutorial(solicitante, "Solicitante; drop", "COMPLETADO"));
		assertThrows(ApiException.class, () -> auth.marcarTutorial(solicitante, "", "COMPLETADO"));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
