package com.tdem.logistica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-L090: un usuario, una sesion. Ingresar en otro dispositivo deja sin
 * efecto la sesion anterior ("Tu sesion se abrio en otro dispositivo") y cerrar
 * sesion invalida el token aunque alguien lo haya copiado.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class SesionUnicaTest {

	private static EmbeddedPostgres pg;

	@Autowired
	private WebApplicationContext contexto;

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
	void elSegundoIngresoCierraLaPrimeraSesion() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

		Cookie primera = ingresar(mvc);
		mvc.perform(get("/api/auth/me").cookie(primera))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.result.sesion").isNotEmpty());

		Cookie segunda = ingresar(mvc);
		assertNotEquals(primera.getValue(), segunda.getValue());

		mvc.perform(get("/api/auth/me").cookie(primera))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESION_REEMPLAZADA"))
				.andExpect(jsonPath("$.message").value("Tu sesión se abrió en otro dispositivo."));
		mvc.perform(get("/api/requerimientos/bandeja").cookie(primera)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/me").cookie(segunda)).andExpect(status().isOk());

		// Cerrar sesion: el token de esa sesion ya no sirve.
		mvc.perform(post("/api/auth/logout").cookie(segunda)).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").cookie(segunda)).andExpect(status().isUnauthorized());
	}

	private static Cookie ingresar(MockMvc mvc) throws Exception {
		Cookie cookie = mvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo\":\"solicitante@logistica.test\",\"password\":\"Logistica2026!\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("auth_token");
		assertNotNull(cookie);
		return cookie;
	}
}
