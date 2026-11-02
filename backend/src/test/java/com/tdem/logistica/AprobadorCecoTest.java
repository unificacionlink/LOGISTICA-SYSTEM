package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
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

/** Lote 12 (DEC-L030): aprobador asignado por CECO, Gerencia por ambito y sin autoaprobacion. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class AprobadorCecoTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private AprobacionService aprobaciones;
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
	void soloDecideElAprobadorDelCecoOGerenciaDelAmbito() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UsuarioPrincipal gerencia = principal("gerencia@logistica.test", "GERENCIA");
		UsuarioPrincipal adminP = principal("admin@logistica.test", "ADMIN");
		UsuarioPrincipal otro = new UsuarioPrincipal(crearAprobador("otro.aprobador@logistica.test"), List.of("APROBADOR"));
		UUID ceco = cecoAdm();

		// --- Solo ADMIN asigna; el aprobador debe tener rol APROBADOR o GERENCIA ---
		assertThrows(ApiException.class, () -> admin.asignarCecosAprobados(aprobador.userId(), List.of(ceco), aprobador));
		assertThrows(ApiException.class, () -> admin.asignarCecosAprobados(solicitante.userId(), List.of(ceco), adminP));
		admin.asignarCecosAprobados(aprobador.userId(), List.of(ceco), adminP);

		// --- El REQ enviado queda asignado: el otro aprobador no lo ve ni lo decide ---
		RequerimientoDto req = requerimientos.crear(solicitud(ceco), solicitante);
		requerimientos.enviar(req.id(), solicitante);
		UUID aprobacion = requerimientos.obtener(req.id(), aprobador).pendingApprovalId();
		assertNotNull(aprobacion);
		assertTrue(aprobaciones.listarPendientes(otro).isEmpty());
		assertThrows(ApiException.class, () -> requerimientos.obtener(req.id(), otro));
		assertThrows(ApiException.class, () -> aprobaciones.decidir(aprobacion,
				new DecidirAprobacionRequest("APROBADO", null, null), otro));

		// --- Gerencia con el CECO entre los suyos tambien decide ---
		assertEquals(1, aprobaciones.listarPendientes(gerencia).size());
		assertNotNull(requerimientos.obtener(req.id(), gerencia).pendingApprovalId());

		// --- Asignar el CECO a otro aprobador (desde su usuario) mueve lo pendiente y queda auditado ---
		admin.asignarCecosAprobados(otro.userId(), List.of(ceco), adminP);
		assertEquals(otro.userId(), jdbc.queryForObject("select approver_user_id from ceco where ceco_id = ?", UUID.class, ceco));
		assertEquals(1, aprobaciones.listarPendientes(otro).size());
		assertTrue(aprobaciones.listarPendientes(aprobador).isEmpty());
		assertEquals(2, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'CECO' and entidad_id = ? and accion = 'ASIGNAR_APROBADOR'""",
				Integer.class, ceco));
		assertEquals("EN_ATENCION", aprobaciones.decidir(aprobacion,
				new DecidirAprobacionRequest("APROBADO", null, null), otro).status());
		assertNotNull(requerimientos.obtener(req.id(), otro), "quien decidio sigue viendo el REQ");

		// --- Quitarlo de la lista del usuario deja el CECO sin aprobador ---
		admin.asignarCecosAprobados(otro.userId(), List.of(), adminP);
		assertNull(jdbc.queryForObject("select approver_user_id from ceco where ceco_id = ?", UUID.class, ceco));
		admin.asignarCecosAprobados(otro.userId(), List.of(ceco), adminP);

		// --- Nadie decide su propio REQ, ni siendo ADMIN ---
		UsuarioPrincipal adminSolicitante = new UsuarioPrincipal(adminP.userId(), List.of("ADMIN", "SOLICITANTE"));
		RequerimientoDto propio = requerimientos.crear(solicitud(ceco), adminSolicitante);
		requerimientos.enviar(propio.id(), adminSolicitante);
		assertNull(requerimientos.obtener(propio.id(), adminSolicitante).pendingApprovalId());
		UUID aprobacionPropia = requerimientos.obtener(propio.id(), otro).pendingApprovalId();
		assertNotNull(aprobacionPropia);
		assertThrows(ApiException.class, () -> aprobaciones.decidir(aprobacionPropia,
				new DecidirAprobacionRequest("APROBADO", null, null), adminSolicitante));
	}

	// ------------------------------------------------------------------

	private UUID cecoAdm() {
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.code = 'ADM'""", UUID.class);
	}

	private CrearRequerimientoRequest solicitud(UUID ceco) {
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		return new CrearRequerimientoRequest(ceco, "NORMAL", "CONSUMO", "Reposicion de materiales de oficina",
				LocalDate.now(ZoneId.of("America/Lima")).plusDays(10),
				List.of(new ItemRequerimientoRequest(variante, BigDecimal.ONE, null, null)));
	}

	private UUID crearAprobador(String correo) {
		UUID id = jdbc.queryForObject("""
				insert into usuario (corporate_email, display_name, company_id, fuente)
				select ?, 'Otro Aprobador', company_id, 'MANUAL' from empresa where code = 'TDEMSRL'
				returning user_id""", UUID.class, correo);
		jdbc.update("""
				insert into usuario_rol (user_id, role_id, company_id)
				select u.user_id, r.role_id, u.company_id from usuario u join rol r on r.code = 'APROBADOR'
				where u.user_id = ?""", id);
		return id;
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(
				jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo),
				List.of(rol));
	}
}
