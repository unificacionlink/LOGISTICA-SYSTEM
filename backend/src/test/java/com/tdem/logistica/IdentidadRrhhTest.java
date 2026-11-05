package com.tdem.logistica;

import com.sun.net.httpserver.HttpServer;
import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.auth.AuthService;
import com.tdem.logistica.modules.auth.UsuarioDto;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.identidad.PersonalSyncService;
import com.tdem.logistica.modules.identidad.dto.*;
import com.tdem.logistica.security.AccesoVigente;
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 8a (DEC-L024) contra Postgres real y un RRHH falso por HTTP que imita
 * GET /api/integraciones/personal-activo y /cecos (header X-Service-Token).
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class IdentidadRrhhTest {

	private static final String TOKEN = "token-de-prueba";
	private static EmbeddedPostgres pg;
	private static HttpServer rrhh;
	/** Lo que responde el RRHH falso (cada test lo arma). */
	private static volatile String personal = "[]";
	private static volatile String tokenRecibido;
	/** Catalogo de CECO del RRHH falso; null = RRHH antiguo sin el endpoint (404). */
	private static volatile String cecos = null;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private PersonalSyncService syncService;
	@Autowired private AdminService adminService;
	@Autowired private AuthService authService;
	@Autowired private AccesoVigente accesoVigente;

	@DynamicPropertySource
	static void bd(DynamicPropertyRegistry registry) throws IOException, SQLException {
		pg = BdRrhhSimulada.iniciar();
		registry.add("spring.datasource.url", () -> BdRrhhSimulada.jdbcUrl(pg));
		registry.add("spring.datasource.username", () -> BdRrhhSimulada.USUARIO_APP);
		registry.add("spring.datasource.password", () -> BdRrhhSimulada.PASSWORD_APP);
		registry.add("app.jwt.secret", () -> "clave-de-prueba-solo-para-tests-0123456789-abcdefghijklmnop");

		rrhh = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		rrhh.createContext("/api/integraciones/personal-activo", ex -> {
			tokenRecibido = ex.getRequestHeaders().getFirst("X-Service-Token");
			byte[] cuerpo = ("{\"ok\":true,\"message\":\"ok\",\"result\":" + personal + "}").getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(200, cuerpo.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(cuerpo);
			}
		});
		rrhh.createContext("/api/integraciones/cecos", ex -> {
			if (cecos == null) {
				ex.sendResponseHeaders(404, -1);
				ex.close();
				return;
			}
			byte[] cuerpo = ("{\"ok\":true,\"message\":\"ok\",\"result\":" + cecos + "}").getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(200, cuerpo.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(cuerpo);
			}
		});
		rrhh.start();
		registry.add("app.rrhh.api-url", () -> "http://127.0.0.1:" + rrhh.getAddress().getPort() + "/");
		registry.add("app.rrhh.service-token", () -> TOKEN);
	}

	@AfterAll
	static void detener() throws IOException {
		if (rrhh != null) {
			rrhh.stop(0);
		}
		if (pg != null) {
			pg.close();
		}
	}

	private static String persona(String dni, String nombre, String correo, String empresa, String ceco, String cecoNombre) {
		String c = ceco == null ? "null" : "{\"numero\":\"" + ceco + "\",\"nombre\":\"" + cecoNombre + "\"}";
		String mail = correo == null ? "null" : "\"" + correo + "\"";
		return "{\"dni\":\"" + dni + "\",\"nombre_completo\":\"" + nombre + "\",\"correo\":" + mail
				+ ",\"empresa\":\"" + empresa + "\",\"ceco\":" + c + ",\"cargo\":\"Tecnico\"}";
	}

	@Test
	void sincronizaAdministraYObligaCambioDeContrasena() {
		UsuarioPrincipal admin = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		UsuarioPrincipal noAdmin = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));

		// Alta MANUAL previa con el mismo correo que luego llega de RRHH: debe convertirse, no duplicarse.
		jdbc.update("""
				insert into usuario (corporate_email, display_name, company_id, fuente)
				select 'rosa.quispe@tdem.com.pe', 'Rosa (manual)', company_id, 'MANUAL' from empresa where code = 'TDEMSRL'""");

		// --- Sync 1 (DEC-L041): trae a todo el personal, tenga o no correo, sin crear cuentas ---
		String sync1 = "[" + String.join(",",
				persona("40000001", "Ana Torres", "ana.torres@tdem.com.pe", "TDEMSRL", "0800", "LIMA"),
				persona("40000002", "Rosa Quispe", "Rosa.Quispe@tdem.com.pe", "TDEMSRL", "0800", "LIMA"),
				persona("40000003", "Luis Paz", "luis.paz@goydel.com.pe", "GOYDELSAC", "0100", "PIURA"),
				persona("40000004", "Sin Empresa", "x@otra.com", "OTRA", null, null),
				persona("40000005", "Pedro Sin Correo", null, "TDEMSRL", "0800", "LIMA")) + "]";
		personal = sync1;
		SyncResultadoDto r1 = syncService.sincronizar();
		assertEquals(TOKEN, tokenRecibido, "manda el token de servicio");
		assertEquals(5, r1.recibidos());
		assertEquals(4, r1.creados(), "Ana, Rosa, Luis y Pedro entran al padron");
		assertEquals(2, r1.cecosCreados());
		assertTrue(r1.avisos().stream().anyMatch(a -> a.contains("OTRA")), r1.avisos().toString());
		assertEquals(3, jdbc.queryForObject("""
				select count(*) from personal p join empresa e on e.company_id = p.company_id
				where e.code = 'TDEMSRL' and p.active""", Integer.class));
		assertEquals(0, jdbc.queryForObject(
				"select count(*) from usuario where employee_external_id in ('40000001', '40000005')", Integer.class),
				"el sync ya no crea cuentas");

		// Rosa ya tenia cuenta MANUAL con su correo: se vincula y pasa a RR.HH., sin duplicarse.
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from usuario where corporate_email = 'rosa.quispe@tdem.com.pe'", Integer.class));
		assertEquals("RRHH_SYNC", jdbc.queryForObject(
				"select fuente from usuario where employee_external_id = '40000002'", String.class));
		assertNotNull(jdbc.queryForObject("select user_id from personal where dni = '40000002'", UUID.class));

		// Repetir la misma respuesta no cambia nada.
		SyncResultadoDto repetido = syncService.sincronizar();
		assertEquals(0, repetido.creados());
		assertEquals(0, repetido.actualizados());

		// --- Administracion: solo ADMIN ---
		assertThrows(ApiException.class, () -> adminService.datos(noAdmin));
		assertThrows(ApiException.class, () -> adminService.personal(noAdmin));
		List<PersonalAdminDto> padron = adminService.personal(admin);
		PersonalAdminDto pedro = padron.stream().filter(p -> p.dni().equals("40000005")).findFirst().orElseThrow();
		assertNull(pedro.correo());
		assertNull(pedro.userId());
		PersonalAdminDto anaP = padron.stream().filter(p -> p.dni().equals("40000001")).findFirst().orElseThrow();
		assertEquals("0800", anaP.cecoCode());

		// --- Dar acceso: cuenta con correo, roles, su CECO y contrasena temporal ---
		DarAccesoDto acceso = adminService.darAcceso(anaP.id(),
				new DarAccesoRequest("Ana.Torres@tdem.com.pe", List.of("SOLICITANTE")), admin);
		assertNotNull(acceso.acceso());
		assertEquals("ana.torres@tdem.com.pe", acceso.acceso().correo());
		UUID ana = jdbc.queryForObject("select user_id from usuario where employee_external_id = '40000001'", UUID.class);
		assertEquals(ana, acceso.personal().stream().filter(p -> p.dni().equals("40000001")).findFirst().orElseThrow().userId());
		assertEquals("RRHH_SYNC", jdbc.queryForObject("select fuente from usuario where user_id = ?", String.class, ana),
				"la cuenta de RR.HH. la sigue controlando el sync");
		assertEquals("0800", jdbc.queryForObject("""
				select c.code from usuario_ceco uc join ceco c on c.ceco_id = uc.ceco_id where uc.user_id = ?""",
				String.class, ana));
		assertEquals(List.of("SOLICITANTE"), accesoVigente.obtener(ana).roles());
		assertThrows(ApiException.class, () -> adminService.darAcceso(anaP.id(),
				new DarAccesoRequest("otra@tdem.com.pe", List.of("SOLICITANTE")), admin), "ya tiene cuenta");
		assertThrows(ApiException.class, () -> adminService.darAcceso(pedro.id(),
				new DarAccesoRequest("ana.torres@tdem.com.pe", List.of("SOLICITANTE")), admin), "correo de otro");
		assertThrows(ApiException.class, () -> adminService.darAcceso(pedro.id(),
				new DarAccesoRequest("pedro@tdem.com.pe", List.of("JEFE")), admin), "rol invalido");
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from log_cambios where entidad = 'PERSONAL' and accion = 'DAR_ACCESO'", Integer.class));

		AdminDatosDto datos = adminService.datos(admin);
		assertTrue(datos.syncConfigurado());
		assertNotNull(datos.ultimaSincronizacion());
		UsuarioAdminDto anaDto = datos.usuarios().stream().filter(u -> u.id().equals(ana)).findFirst().orElseThrow();
		assertTrue(anaDto.tieneAcceso());
		assertTrue(anaDto.debeCambiarPassword());
		assertTrue(datos.empresas().stream().anyMatch(e -> e.code().equals("METRIN") && e.altaManual()));
		assertTrue(datos.empresas().stream().anyMatch(e -> e.code().equals("TDEMSRL") && !e.altaManual()));

		// --- Roles ---
		adminService.asignarRoles(ana, List.of("SOLICITANTE", "LOGISTICA"), admin);
		assertEquals(List.of("SOLICITANTE", "LOGISTICA"), accesoVigente.obtener(ana).roles(), "cache invalidada al cambiar roles");
		assertThrows(ApiException.class, () -> adminService.asignarRoles(ana, List.of("JEFE"), admin));
		assertThrows(ApiException.class, () -> adminService.asignarRoles(admin.userId(), List.of("LOGISTICA"), admin),
				"no se quita su propio ADMIN");

		// --- Contrasena temporal y cambio obligatorio ---
		PasswordTemporalDto temporal = adminService.passwordTemporal(ana, admin);
		assertEquals(10, temporal.password().length());
		AuthService.LoginResult login = authService.login("ana.torres@tdem.com.pe", temporal.password());
		assertTrue(login.usuario().debeCambiarPassword());
		assertThrows(ApiException.class, () -> authService.cambiarPassword(ana, "incorrecta", "NuevaClave2026"));
		assertThrows(ApiException.class, () -> authService.cambiarPassword(ana, temporal.password(), "corta1"));
		assertThrows(ApiException.class, () -> authService.cambiarPassword(ana, temporal.password(), "sololetrasaqui"));
		UsuarioDto cambiado = authService.cambiarPassword(ana, temporal.password(), "NuevaClave2026");
		assertFalse(cambiado.debeCambiarPassword());
		assertFalse(authService.login("ana.torres@tdem.com.pe", "NuevaClave2026").usuario().debeCambiarPassword());

		// --- Alta manual solo en empresas sin padron en RRHH ---
		adminService.crearCeco(new CrearCecoRequest("METRIN", "adm", "Administracion"), admin);
		UUID cecoMetrin = jdbc.queryForObject(
				"select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id where e.code = 'METRIN' and c.code = 'ADM'",
				UUID.class);
		assertThrows(ApiException.class, () -> adminService.crearCeco(new CrearCecoRequest("TDEMSRL", "X1", "X"), admin));
		adminService.crearUsuario(new CrearUsuarioRequest("Carla Metrin", "Carla@Metrin.pe", null, "METRIN",
				cecoMetrin, List.of("SOLICITANTE"), null), admin);
		assertEquals("MANUAL", jdbc.queryForObject(
				"select fuente from usuario where corporate_email = 'carla@metrin.pe'", String.class));
		assertThrows(ApiException.class, () -> adminService.crearUsuario(new CrearUsuarioRequest("Otro", "otro@tdem.com.pe",
				null, "TDEMSRL", null, List.of("SOLICITANTE"), null), admin), "TDEM viene de RRHH");
		assertThrows(ApiException.class, () -> adminService.cambiarActivo(ana, false, admin),
				"el estado de un usuario de RRHH lo controla el sync");

		// --- Sync 2: Rosa ya no esta activa en RRHH -> cesa en el padron y su cuenta pierde acceso ---
		UUID rosa = jdbc.queryForObject("select user_id from usuario where employee_external_id = '40000002'", UUID.class);
		assertTrue(accesoVigente.obtener(rosa).activo());
		personal = "[" + String.join(",",
				persona("40000001", "Ana Torres", "ana.torres@tdem.com.pe", "TDEMSRL", "0800", "LIMA"),
				persona("40000003", "Luis Paz", "luis.paz@goydel.com.pe", "GOYDELSAC", "0100", "PIURA"),
				persona("40000005", "Pedro Sin Correo", null, "TDEMSRL", "0800", "LIMA")) + "]";
		adminService.sincronizar(admin);
		assertEquals(1, syncService.ultimoResultado().desactivados(), "Rosa: su ficha y su cuenta cuentan una vez");
		assertFalse(jdbc.queryForObject("select active from personal where dni = '40000002'", Boolean.class));
		assertFalse(accesoVigente.obtener(rosa).activo(), "RF-041: sin acceso apenas se sincroniza");
		assertEquals(List.of("SOLICITANTE", "LOGISTICA"), accesoVigente.obtener(ana).roles(), "el sync no pisa roles asignados");
		assertTrue(jdbc.queryForObject(
				"select active from usuario where corporate_email = 'carla@metrin.pe'", Boolean.class), "MANUAL no se toca");

		// --- Sync 3: RRHH responde vacio -> no se desactiva a nadie ---
		personal = "[]";
		SyncResultadoDto vacio = syncService.sincronizar();
		assertEquals(0, vacio.desactivados());
		assertTrue(vacio.avisos().stream().anyMatch(a -> a.contains("no se desactivo")));
		assertTrue(accesoVigente.obtener(ana).activo());

		// --- Sync 4 (DEC-L031): catalogo completo de CECO, tengan o no personal ---
		personal = "[" + String.join(",",
				persona("40000001", "Ana Torres", "ana.torres@tdem.com.pe", "TDEMSRL", "0800", "LIMA")) + "]";
		cecos = "[{\"numero\":\"0800\",\"nombre\":\"LIMA\",\"empresa\":\"TDEMSRL\"},"
				+ "{\"numero\":\"900\",\"nombre\":\"CALLAO MAYUS\",\"empresa\":\"TDEMSRL\"},"
				+ "{\"numero\":\"0900\",\"nombre\":\" Callao   Puerto \",\"empresa\":\"TDEMSRL\"},"
				+ "{\"numero\":\"0100\",\"nombre\":\"PIURA\",\"empresa\":\"GOYDELSAC\"}]";
		SyncResultadoDto catalogo = syncService.sincronizar();
		assertEquals(1, catalogo.cecosCreados(), "0900 llega sin personal; 900 es el mismo CECO (DEC-L032)");
		assertEquals("CALLAO PUERTO", jdbc.queryForObject("select name from ceco where code = '0900'", String.class),
				"manda el nombre del codigo de 4 digitos, en mayusculas (DEC-L033)");
		assertEquals(2, catalogo.cecosDesactivados(), "ADM y OPS de relleno (V1) salen de TDEMSRL");
		assertEquals(List.of("0800", "0900"), jdbc.queryForList("""
				select c.code from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.active order by c.code""", String.class));
		assertTrue(jdbc.queryForObject("select active from ceco where ceco_id = ?", Boolean.class, cecoMetrin),
				"empresas de alta manual no se tocan");

		// --- Catalogo vacio: no se desactiva ningun CECO ---
		cecos = "[]";
		assertEquals(0, syncService.sincronizar().cecosDesactivados());
		assertEquals(2, jdbc.queryForObject("""
				select count(*) from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.active""", Integer.class));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
