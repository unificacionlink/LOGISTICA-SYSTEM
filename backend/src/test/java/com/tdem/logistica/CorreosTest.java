package com.tdem.logistica;

import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.notificaciones.AvisosPlataforma;
import com.tdem.logistica.modules.notificaciones.DespachadorCorreos;
import com.tdem.logistica.modules.notificaciones.EnvioCorreo;
import com.tdem.logistica.modules.notificaciones.RecordatorioAprobaciones;
import com.tdem.logistica.modules.registro.RegistroPlataforma;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** DEC-L066: correos automaticos por bandeja de salida (RF-007, RF-063). */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class CorreosTest {

	/** Proveedor falso: guarda lo enviado o falla a pedido. */
	static class EnvioFalso implements EnvioCorreo {
		final List<Mensaje> enviados = new CopyOnWriteArrayList<>();
		volatile boolean fallar;

		@Override
		public boolean configurado() {
			return true;
		}

		@Override
		public String enviar(Mensaje m) {
			if (fallar) {
				throw new CorreoNoEnviado("Resend respondio 503: no disponible");
			}
			enviados.add(m);
			return "re_" + m.id();
		}
	}

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		EnvioFalso envioFalso() {
			return new EnvioFalso();
		}
	}

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private AprobacionService aprobaciones;
	@Autowired private AdminService admin;
	@Autowired private AvisosPlataforma avisosPlataforma;
	@Autowired private DespachadorCorreos despachador;
	@Autowired private RecordatorioAprobaciones recordatorio;
	@Autowired private RegistroPlataforma registro;
	@Autowired private TransactionTemplate tx;
	@Autowired private EnvioFalso envio;

	@DynamicPropertySource
	static void bd(DynamicPropertyRegistry registry) throws IOException, SQLException {
		pg = BdRrhhSimulada.iniciar();
		registry.add("spring.datasource.url", () -> BdRrhhSimulada.jdbcUrl(pg));
		registry.add("spring.datasource.username", () -> BdRrhhSimulada.USUARIO_APP);
		registry.add("spring.datasource.password", () -> BdRrhhSimulada.PASSWORD_APP);
		registry.add("app.jwt.secret", () -> "clave-de-prueba-solo-para-tests-0123456789-abcdefghijklmnop");
		// El despachador lo llama el test, no el reloj.
		registry.add("app.correo.intervalo-ms", () -> "3600000");
	}

	@AfterAll
	static void detener() throws IOException {
		if (pg != null) {
			pg.close();
		}
	}

	@Test
	void avisosEnvioReintentosYRecordatorio() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UsuarioPrincipal gerencia = principal("gerencia@logistica.test", "GERENCIA");
		UsuarioPrincipal adminP = principal("admin@logistica.test", "ADMIN");
		UUID ceco = cecoAdm();
		admin.asignarCecosAprobados(aprobador.userId(), List.of(ceco), adminP);

		// --- Enviar: al aprobador asignado y a la Gerencia del CECO; nunca al solicitante ---
		RequerimientoDto req = requerimientos.crear(solicitud(ceco), solicitante);
		requerimientos.enviar(req.id(), solicitante);
		assertEquals(Set.of(aprobador.userId(), gerencia.userId()),
				Set.copyOf(destinatarios(req.id(), "APROBACION_PENDIENTE")));
		String html = jdbc.queryForObject("""
				select html from correo_saliente where entity_id = ? and event_type = 'APROBACION_PENDIENTE' limit 1""",
				String.class, req.id());
		assertTrue(html.contains("/aprobaciones"), "el boton lleva a la plataforma");

		// DEC-L101: Logistica recibe el correo del REQ emitido.
		UUID logistica = principal("logistica@logistica.test", "LOGISTICA").userId();
		assertEquals(List.of(logistica), destinatarios(req.id(), "REQ_ENVIADO"));

		// --- Aprobar: el solicitante se entera en su campana, no por correo (DEC-L076); Logistica, por correo (DEC-L101) ---
		UUID aprobacion = requerimientos.obtener(req.id(), aprobador).pendingApprovalId();
		aprobaciones.decidir(aprobacion, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
		assertEquals(List.of(logistica), destinatarios(req.id(), "REQ_APROBADO"));
		var bandeja = avisosPlataforma.bandeja(solicitante);
		assertEquals(1, bandeja.noLeidas());
		assertEquals("REQ_APROBADO", bandeja.items().get(0).tipo());
		assertEquals(req.id(), bandeja.items().get(0).requestId());
		assertEquals(0, avisosPlataforma.bandeja(aprobador).noLeidas(), "quien decide no se avisa a si mismo");
		assertEquals(0, avisosPlataforma.marcarLeida(bandeja.items().get(0).id(), aprobador).noLeidas());
		assertEquals(1, avisosPlataforma.bandeja(solicitante).noLeidas(), "nadie marca como leida la de otro");
		assertEquals(0, avisosPlataforma.marcarLeida(bandeja.items().get(0).id(), solicitante).noLeidas());

		// --- Observar (RF-007): con el motivo, escapado ---
		RequerimientoDto otro = requerimientos.crear(solicitud(ceco), solicitante);
		requerimientos.enviar(otro.id(), solicitante);
		UUID aprobacionOtro = requerimientos.obtener(otro.id(), aprobador).pendingApprovalId();
		aprobaciones.decidir(aprobacionOtro,
				new DecidirAprobacionRequest("OBSERVADO", null, "<b>Falta</b> el sustento & la cotizacion"), aprobador);
		assertEquals(List.of(), destinatarios(otro.id(), "REQ_OBSERVADO"));
		String observado = jdbc.queryForObject("""
				select body from notificacion where request_id = ? and kind = 'REQ_OBSERVADO'""", String.class, otro.id());
		assertTrue(observado.contains("<b>Falta</b> el sustento & la cotizacion"), "el motivo va tal cual: la pantalla lo escapa");

		// --- Si la accion se revierte, no queda aviso ---
		int antes = contar("select count(*) from notificacion");
		tx.executeWithoutResult(estado -> {
			avisosPlataforma.decision(req.id(), "RECHAZADO", "No procede", aprobador.userId());
			estado.setRollbackOnly();
		});
		assertEquals(antes, contar("select count(*) from notificacion"));

		// --- El despachador envia lo pendiente ---
		int pendientes = total();
		assertEquals(pendientes, despachador.enviarPendientes());
		assertEquals(pendientes, envio.enviados.size());
		assertEquals(0, contar("select count(*) from correo_saliente where status <> 'ENVIADO'"));
		assertEquals(0, contar("select count(*) from correo_saliente where provider_message_id is null"));

		// --- Recordatorio del dia siguiente: una vez por persona, aunque corra dos veces ---
		LocalDate manana = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1);
		RequerimientoDto pendiente = requerimientos.crear(solicitud(ceco), solicitante);
		requerimientos.enviar(pendiente.id(), solicitante);
		assertTrue(recordatorio.recordar(manana) >= 1);
		recordatorio.recordar(manana);
		assertEquals(2, destinatarios(pendiente.id(), "RECORDATORIO_APROBACION").size(),
				"aprobador y Gerencia, sin repetirse");

		// --- RF-063: un fallo se reintenta y al quinto intento queda en ERROR y en el registro ---
		envio.fallar = true;
		UUID uno = jdbc.queryForObject("""
				select correo_id from correo_saliente where status = 'PENDIENTE' order by created_at limit 1""",
				UUID.class);
		jdbc.update("update correo_saliente set status = 'ENVIADO' where status = 'PENDIENTE' and correo_id <> ?", uno);
		for (int i = 1; i <= 5; i++) {
			jdbc.update("update correo_saliente set next_attempt_at = now() where correo_id = ?", uno);
			assertEquals(0, despachador.enviarPendientes());
			assertEquals(i, jdbc.queryForObject("select attempts from correo_saliente where correo_id = ?",
					Integer.class, uno));
		}
		assertEquals("ERROR", jdbc.queryForObject("select status from correo_saliente where correo_id = ?",
				String.class, uno));
		assertTrue(jdbc.queryForObject("select last_error from correo_saliente where correo_id = ?", String.class, uno)
				.contains("503"));
		registro.volcar();
		assertEquals(1, contar("select count(*) from registro_error where error_type = 'CorreoNoEnviado'"));
	}

	// ------------------------------------------------------------------

	private List<UUID> destinatarios(UUID requestId, String evento) {
		return jdbc.queryForList("""
				select recipient_user_id from correo_saliente where entity_id = ? and event_type = ?
				order by recipient_user_id""", UUID.class, requestId, evento);
	}

	private int total() {
		return contar("select count(*) from correo_saliente");
	}

	private int contar(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private UUID cecoAdm() {
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.code = 'ADM'""", UUID.class);
	}

	private CrearRequerimientoRequest solicitud(UUID ceco) {
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		return new CrearRequerimientoRequest(ceco, "URGENTE", "CONSUMO", "Reposicion de materiales de oficina",
				LocalDate.now(ZoneId.of("America/Lima")).plusDays(10),
				List.of(new ItemRequerimientoRequest(variante, BigDecimal.ONE, null, null)));
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(
				jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo),
				List.of(rol));
	}
}
