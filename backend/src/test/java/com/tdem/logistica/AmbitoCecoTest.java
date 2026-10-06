package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.requerimientos.dto.CecoDto;
import com.tdem.logistica.modules.requerimientos.dto.CrearRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.ItemRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DEC-L029: el SOLICITANTE solo pide para los CECOs que ADMIN le asigno, de cualquier empresa. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class AmbitoCecoTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private AdminService admin;

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
	void solicitantePideSoloParaSusCecosDeCualquierEmpresa() {
		UUID tdem1 = ceco("TDEMSRL", "AMB-T1");
		UUID tdem2 = ceco("TDEMSRL", "AMB-T2");
		UUID goydel = ceco("GOYDELSAC", "AMB-G1");
		UUID inactivo = ceco("GOYDELSAC", "AMB-G2");
		jdbc.update("update ceco set active = false where ceco_id = ?", inactivo);

		UUID solicitanteId = usuario("solicitante@logistica.test");
		UsuarioPrincipal solicitante = new UsuarioPrincipal(solicitanteId, List.of("SOLICITANTE"));
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UsuarioPrincipal adminP = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));

		// ADMIN reemplaza el ambito: un CECO de TDEM y uno de GOYDEL.
		admin.asignarCecos(solicitanteId, List.of(tdem1, goydel), adminP);
		assertThrows(ApiException.class, () -> admin.asignarCecos(solicitanteId, List.of(inactivo), adminP),
				"no se asignan CECOs inactivos");
		assertThrows(ApiException.class, () -> admin.asignarCecos(solicitanteId, List.of(tdem1), solicitante),
				"solo ADMIN asigna");

		List<UUID> visibles = requerimientos.obtenerDatosFormulario(solicitante).cecos().stream()
				.map(CecoDto::cecoId).toList();
		assertEquals(2, visibles.size());
		assertTrue(visibles.containsAll(List.of(tdem1, goydel)));
		assertTrue(requerimientos.obtenerDatosFormulario(logistica).cecos().stream()
				.anyMatch(c -> c.cecoId().equals(tdem2)), "LOGISTICA ve todos los activos");

		// No puede pedir para un CECO no asignado.
		assertThrows(ApiException.class, () -> requerimientos.crear(solicitud(tdem2), solicitante));

		// La empresa del REQ sale del CECO elegido.
		RequerimientoDto req = requerimientos.crear(solicitud(goydel), solicitante);
		assertEquals("GOYDELSAC", req.empresaCode());
		assertTrue(req.code().startsWith("REQ-GOYDELSAC-"), req.code());

		// Al editar no cambia de empresa.
		assertThrows(ApiException.class, () -> requerimientos.actualizar(req.id(), solicitud(tdem1), solicitante));

		// Sin CECOs asignados no ve ninguno.
		admin.asignarCecos(solicitanteId, List.of(), adminP);
		assertTrue(requerimientos.obtenerDatosFormulario(solicitante).cecos().isEmpty());
		// Pero conserva el CECO de un REQ ya creado al corregirlo.
		requerimientos.actualizar(req.id(), solicitud(goydel), solicitante);
	}

	// ------------------------------------------------------------------

	private UUID ceco(String empresa, String code) {
		return jdbc.queryForObject("""
				insert into ceco (company_id, code, name, active)
				select company_id, ?, ?, true from empresa where code = ?
				returning ceco_id""", UUID.class, code, "Centro " + code, empresa);
	}

	private CrearRequerimientoRequest solicitud(UUID ceco) {
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		return new CrearRequerimientoRequest(ceco, "NORMAL", "CONSUMO", "Prueba de ambito por CECO",
				LocalDate.now(ZoneId.of("America/Lima")).plusDays(10),
				List.of(new ItemRequerimientoRequest(variante, BigDecimal.ONE, null, null)));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
