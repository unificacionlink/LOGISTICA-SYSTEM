package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.identidad.PersonalManualService;
import com.tdem.logistica.modules.identidad.dto.EditarUsuarioRequest;
import com.tdem.logistica.modules.identidad.dto.PersonalCargaDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DEC-L028: padron de personal de GOYDEL y METRIN por Excel y edicion individual. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class PersonalManualTest {

	private static final List<String> CABECERA = List.of("Empresa", "DNI", "Nombre completo", "Correo", "Cargo",
			"CECO codigo", "CECO nombre", "Rol inicial", "Activo");

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private PersonalManualService personal;
	@Autowired private AdminService adminService;

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
	void altaEdicionYCesePorExcel() throws IOException {
		UsuarioPrincipal admin = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));

		assertThrows(ApiException.class, () -> personal.plantilla(logistica), "solo ADMIN");

		// --- Archivo con errores: no se aplica nada ---
		MockMultipartFile conErrores = excel(
				fila("GOYDELSAC", "40111222", "Luis Goydel", "luis@goydel.pe", "Supervisor", "OPE", "Operaciones", "", ""),
				fila("TDEMSRL", "40111223", "Otro Tdem", "otro@tdem.pe", "", "", "", "", ""),
				fila("METRIN", "40111222", "Duplicado", "dup@metrin.pe", "", "", "", "", ""),
				fila("METRIN", "123", "Dni Corto Ok", "corto@metrin.pe", "", "", "", "", ""),
				fila("METRIN", "40111224", "Sin Ceco", "sinceco@metrin.pe", "", "ZZZ", "", "", ""),
				fila("METRIN", "40111225", "Admin No", "admin@metrin.pe", "", "", "", "ADMIN", ""),
				fila("METRIN", "40111226", "Alta Inactiva", "inactiva@metrin.pe", "", "", "", "", "NO"));
		PersonalCargaDto v = personal.validar(conErrores, admin);
		assertEquals(5, v.conError(), v.filas().toString());
		assertEquals("00000123", v.filas().get(3).dni(), "rellena los ceros que Excel quita");
		assertNull(v.filas().get(3).error());
		assertThrows(ApiException.class, () -> personal.confirmar(conErrores, admin));
		assertEquals(0, contar("select count(*) from usuario where corporate_email = 'luis@goydel.pe'"));

		// --- Alta: crea usuarios, CECO nuevo y rol inicial ---
		MockMultipartFile alta = excel(
				fila("GOYDELSAC", "40111222", "Luis Goydel", "Luis@Goydel.pe", "Supervisor", "OPE", "Operaciones", "", ""),
				fila("goydelsac", "40111227", "Rosa Goydel", "rosa@goydel.pe", "Asistente", "OPE", "", "APROBADOR", "SI"),
				fila("METRIN", "40111228", "Marco Metrin", "marco@metrin.pe", "", "", "", "LOGISTICA", ""));
		PersonalCargaDto r = personal.confirmar(alta, admin);
		assertEquals(3, r.nuevos());
		assertTrue(r.aplicada());
		UUID luis = usuario("luis@goydel.pe");
		assertEquals("MANUAL", jdbc.queryForObject("select fuente from usuario where user_id = ?", String.class, luis));
		assertEquals("Supervisor", jdbc.queryForObject("select cargo from usuario where user_id = ?", String.class, luis));
		assertEquals(1, contar("""
				select count(*) from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'GOYDELSAC' and c.code = 'OPE' and c.name = 'Operaciones'"""));
		assertEquals(2, contar("""
				select count(*) from usuario_ceco uc join ceco c on c.ceco_id = uc.ceco_id where c.code = 'OPE'"""));
		assertEquals(List.of("SOLICITANTE"), roles("luis@goydel.pe"));
		assertEquals(List.of("APROBADOR"), roles("rosa@goydel.pe"));
		assertEquals(List.of("LOGISTICA"), roles("marco@metrin.pe"));

		// --- La plantilla trae al personal cargado ---
		byte[] plantilla = personal.plantilla(admin);
		try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
			Sheet hoja = wb.getSheet("PERSONAL");
			assertEquals(3, hoja.getLastRowNum());
		}

		// --- Mismo archivo, con cambios: edita, cesa y deja igual ---
		MockMultipartFile cambios = excel(
				fila("GOYDELSAC", "40111222", "Luis Goydel Ramos", "luis@goydel.pe", "Jefe", "", "", "", "SI"),
				fila("GOYDELSAC", "40111227", "Rosa Goydel", "rosa@goydel.pe", "Asistente", "OPE", "", "", "NO"),
				fila("METRIN", "40111228", "Marco Metrin", "marco@metrin.pe", "", "", "", "", ""));
		PersonalCargaDto c = personal.confirmar(cambios, admin);
		assertEquals(1, c.actualizados());
		assertEquals(1, c.desactivados());
		assertEquals(1, c.sinCambios());
		assertEquals("Luis Goydel Ramos", jdbc.queryForObject(
				"select display_name from usuario where user_id = ?", String.class, luis));
		assertFalse(jdbc.queryForObject("select active from usuario where corporate_email = 'rosa@goydel.pe'", Boolean.class));
		assertEquals(1, contar("select count(*) from usuario_ceco where user_id = '" + luis + "'"),
				"CECO en blanco = no se cambia");
		assertEquals(List.of("SOLICITANTE"), roles("luis@goydel.pe"), "el Excel no toca roles de existentes");

		// --- Sin cambios: no hay nada que confirmar ---
		assertThrows(ApiException.class, () -> personal.confirmar(cambios, admin));

		// --- No toca personal de RR.HH. ni correos ajenos ---
		jdbc.update("""
				insert into usuario (corporate_email, display_name, active, fuente, employee_external_id, company_id)
				select 'ana@tdem.pe', 'Ana Rrhh', true, 'RRHH_SYNC', '40999888', company_id from empresa where code = 'TDEMSRL'""");
		String dniRrhh = "40999888";
		{
			PersonalCargaDto rrhh = personal.validar(excel(
					fila("METRIN", dniRrhh, "Intruso", "intruso@metrin.pe", "", "", "", "", "")), admin);
			assertNotNull(rrhh.filas().get(0).error());
		}
		PersonalCargaDto correoAjeno = personal.validar(excel(
				fila("METRIN", "40111229", "Copia", "marco@metrin.pe", "", "", "", "", "")), admin);
		assertTrue(correoAjeno.filas().get(0).error().contains("otro usuario"), correoAjeno.filas().toString());

		// --- Edicion individual ---
		UUID marco = usuario("marco@metrin.pe");
		assertThrows(ApiException.class, () -> adminService.editarUsuario(marco,
				new EditarUsuarioRequest("Marco", "luis@goydel.pe", null, null), admin), "correo de otro");
		adminService.editarUsuario(marco,
				new EditarUsuarioRequest("Marco Metrin Diaz", "marco.diaz@metrin.pe", "40111228", "Almacenero"), admin);
		assertEquals("Almacenero", jdbc.queryForObject("select cargo from usuario where user_id = ?", String.class, marco));
		UUID tdem = usuario("solicitante@logistica.test");
		if (!"MANUAL".equals(jdbc.queryForObject("select fuente from usuario where user_id = ?", String.class, tdem))) {
			assertThrows(ApiException.class, () -> adminService.editarUsuario(tdem,
					new EditarUsuarioRequest("X", "x@x.pe", null, null), admin));
		}
	}

	// ------------------------------------------------------------------

	private static String[] fila(String... valores) {
		return valores;
	}

	private static MockMultipartFile excel(String[]... filas) throws IOException {
		try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet hoja = wb.createSheet("PERSONAL");
			Row h = hoja.createRow(0);
			for (int c = 0; c < CABECERA.size(); c++) {
				h.createCell(c).setCellValue(CABECERA.get(c));
			}
			for (int r = 0; r < filas.length; r++) {
				Row row = hoja.createRow(r + 1);
				for (int c = 0; c < filas[r].length; c++) {
					row.createCell(c).setCellValue(filas[r][c]);
				}
			}
			wb.write(out);
			return new MockMultipartFile("file", "personal.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
		}
	}

	private List<String> roles(String correo) {
		return jdbc.queryForList("""
				select r.code from usuario_rol ur join rol r on r.role_id = ur.role_id
				join usuario u on u.user_id = ur.user_id where u.corporate_email = ? order by r.code""", String.class, correo);
	}

	private int contar(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
