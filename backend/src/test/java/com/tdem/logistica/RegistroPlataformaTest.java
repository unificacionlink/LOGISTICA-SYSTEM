package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.registro.RegistroActividadFilter;
import com.tdem.logistica.modules.registro.RegistroPlataforma;
import com.tdem.logistica.modules.registro.RegistroService;
import com.tdem.logistica.modules.registro.dto.RegistroDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-L060: el registro de la plataforma anota ingresos (correctos y
 * fallidos, sin la clave), operaciones que cambian datos con su resultado,
 * errores del navegador; y solo ADMIN lo consulta.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class RegistroPlataformaTest {

	private static EmbeddedPostgres pg;

	@Autowired private WebApplicationContext contexto;
	@Autowired @Qualifier("springSecurityFilterChain") private Filter seguridad;
	@Autowired private RegistroActividadFilter filtroRegistro;
	@Autowired private RegistroPlataforma registro;
	@Autowired private RegistroService service;
	@Autowired private JdbcTemplate jdbc;
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
	void anotaIngresosOperacionesYErroresYSoloAdminLosVe() throws Exception {
		// Mismo orden que en el servidor: primero la seguridad, luego el registro.
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(seguridad, filtroRegistro).build();
		UUID solicitante = usuario("solicitante@logistica.test");

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo\":\"Solicitante@Logistica.test\",\"password\":\"clave-equivocada\"}"))
				.andExpect(status().isUnauthorized());
		Cookie sesion = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo\":\"solicitante@logistica.test\",\"password\":\"Logistica2026!\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("auth_token");
		assertNotNull(sesion);

		mvc.perform(put("/api/auth/tutoriales/solicitante").cookie(sesion).contentType(MediaType.APPLICATION_JSON)
						.content("{\"estado\":\"COMPLETADO\"}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/registro/errores").cookie(sesion).contentType(MediaType.APPLICATION_JSON)
						.content("{\"mensaje\":\"TypeError: x is undefined\",\"pila\":\"at Pantalla\",\"ruta\":\"/compras\"}"))
				.andExpect(status().isOk());
		registro.volcar();

		UsuarioPrincipal admin = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		RegistroService.Filtros todo = new RegistroService.Filtros(null, null, null, null, null, 0);

		List<RegistroDto.Actividad> ingresos = service.actividad(
				new RegistroService.Filtros(null, null, null, null, "INGRESOS", 0), admin).filas();
		assertEquals(2, ingresos.size());
		RegistroDto.Actividad fallido = ingresos.stream().filter(a -> a.status() == 401).findFirst().orElseThrow();
		assertEquals("solicitante@logistica.test", fallido.detalle(), "queda el correo intentado");
		assertNull(fallido.usuarioId());
		RegistroDto.Actividad correcto = ingresos.stream().filter(a -> a.status() == 200).findFirst().orElseThrow();
		assertEquals(solicitante, correcto.usuarioId());
		assertEquals(0, jdbc.queryForObject(
				"select count(*) from registro_actividad where detail ilike '%clave-equivocada%' or detail ilike '%Logistica2026%'",
				Integer.class), "nunca la clave");

		RegistroDto.Actividad tutorial = service.actividad(
				new RegistroService.Filtros(null, null, solicitante, "tutoriales", null, 0), admin).filas().getFirst();
		assertEquals("PUT", tutorial.metodo());
		assertEquals("auth", tutorial.modulo());
		assertEquals(200, tutorial.status());
		assertEquals(solicitante, tutorial.usuarioId());

		assertEquals(3, service.actividad(todo, admin).filas().size(), "el reporte de errores no se anota como actividad");

		RegistroDto.Error error = service.errores(
				new RegistroService.Filtros(null, null, null, null, "NAVEGADOR", 0), admin).filas().getFirst();
		assertEquals("TypeError: x is undefined", error.mensaje());
		assertEquals("/compras", error.ruta());
		assertEquals(solicitante, error.usuarioId());

		RegistroDto.Resumen resumen = service.resumen(admin);
		assertEquals(1, resumen.ingresosFallidos());
		assertEquals(1, resumen.erroresNavegador());

		UsuarioPrincipal noAdmin = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		assertThrows(ApiException.class, () -> service.actividad(todo, noAdmin));
		assertThrows(ApiException.class, () -> service.errores(todo, noAdmin));
		assertThrows(ApiException.class, () -> service.cambios(todo, noAdmin));
		assertThrows(ApiException.class, () -> service.movimientos(todo, noAdmin));
		assertThrows(ApiException.class, () -> service.resumen(noAdmin));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/registro/actividad")
				.cookie(sesion)).andExpect(status().isForbidden());

		// Las otras vistas responden sobre los datos sembrados (sin errores de SQL).
		assertNotNull(service.cambios(new RegistroService.Filtros(null, null, null, "algo", null, 0), admin));
		assertNotNull(service.movimientos(new RegistroService.Filtros(null, null, null, null, "AJUSTE", 0), admin));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
