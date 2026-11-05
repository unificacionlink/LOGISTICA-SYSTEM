package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.identidad.PersonalManualService;
import com.tdem.logistica.modules.identidad.dto.DarAccesoDto;
import com.tdem.logistica.modules.identidad.dto.DarAccesoRequest;
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

/** DEC-L028/DEC-L041: padron de personal de GOYDEL y METRIN por Excel, acceso y edicion individual. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class PersonalManualTest {

	private static final List<String> CABECERA = List.of("Empresa", "DNI", "Nombre completo", "Correo", "Cargo",
			"CECO codigo", "CECO nombre", "Activo");

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
				fila("GOYDELSAC", "40111222", "Luis Goydel", "luis@goydel.pe", "Supervisor", "OPE", "Operaciones", ""),
				fila("TDEMSRL", "40111223", "Otro Tdem", "otro@tdem.pe", "", "", "", ""),
				fila("GOYDELSAC", "40111222", "Duplicado", "dup@goydel.pe", "", "", "", ""),
				fila("METRIN", "123", "Dni Corto Ok", "corto@metrin.pe", "", "", "", ""),
				fila("METRIN", "40111224", "Sin Ceco", "sinceco@metrin.pe", "", "ZZZ", "", ""),
				fila("METRIN", "40111225", "Correo Malo", "correo-malo", "", "", "", ""),
				fila("METRIN", "40111226", "Alta Inactiva", "inactiva@metrin.pe", "", "", "", "NO"),
				fila("METRIN", "40111222", "Luis En Metrin", "", "", "", "", ""));
		PersonalCargaDto v = personal.validar(conErrores, admin);
		assertEquals(5, v.conError(), v.filas().toString());
		assertEquals("00000123", v.filas().get(3).dni(), "rellena los ceros que Excel quita");
		assertNull(v.filas().get(3).error());
		assertNull(v.filas().get(7).error(), "cada empresa tiene su lista: el mismo DNI en METRIN es otra ficha");
		assertThrows(ApiException.class, () -> personal.confirmar(conErrores, admin));
		assertEquals(0, contar("select count(*) from personal where dni = '40111222'"));

		// --- Alta: llena el padron (con o sin correo) y crea el CECO; no crea cuentas (DEC-L041) ---
		MockMultipartFile alta = excel(
				fila("GOYDELSAC", "40111222", "Luis Goydel", "Luis@Goydel.pe", "Supervisor", "OPE", "Operaciones", ""),
				fila("goydelsac", "40111227", "Rosa Goydel", "rosa@goydel.pe", "Asistente", "OPE", "", "SI"),
				fila("METRIN", "40111228", "Marco Metrin", "marco@metrin.pe", "", "", "", ""),
				fila("METRIN", "40111231", "Juana Sin Correo", "", "", "", "", ""));
		PersonalCargaDto r = personal.confirmar(alta, admin);
		assertEquals(4, r.nuevos());
		assertTrue(r.aplicada());
		assertEquals(4, contar("select count(*) from personal where fuente = 'MANUAL' and active"));
		assertEquals(0, contar("select count(*) from usuario where corporate_email = 'luis@goydel.pe'"));
		assertEquals("Supervisor", jdbc.queryForObject("select position from personal where dni = '40111222'", String.class));
		assertNull(jdbc.queryForObject("select email from personal where dni = '40111231'", String.class));
		assertEquals(1, contar("""
				select count(*) from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'GOYDELSAC' and c.code = 'OPE' and c.name = 'OPERACIONES'"""));
		assertEquals(2, contar("""
				select count(*) from personal p join ceco c on c.ceco_id = p.ceco_id where c.code = 'OPE'"""));

		// --- La plantilla trae al personal cargado ---
		byte[] plantilla = personal.plantilla(admin);
		try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(plantilla))) {
			Sheet hoja = wb.getSheet("PERSONAL");
			assertEquals(4, hoja.getLastRowNum());
		}

		// --- Dar acceso a Luis: cuenta MANUAL con su CECO de planilla ---
		UUID fichaLuis = jdbc.queryForObject("select personal_id from personal where dni = '40111222'", UUID.class);
		DarAccesoDto acceso = adminService.darAcceso(fichaLuis,
				new DarAccesoRequest("luis@goydel.pe", List.of("SOLICITANTE")), admin);
		assertNotNull(acceso.acceso());
		UUID luis = usuario("luis@goydel.pe");
		assertEquals("MANUAL", jdbc.queryForObject("select fuente from usuario where user_id = ?", String.class, luis));
		assertEquals(1, contar("select count(*) from usuario_ceco where user_id = '" + luis + "'"));
		assertEquals(List.of("SOLICITANTE"), roles("luis@goydel.pe"));

		// --- Mismo archivo, con cambios: edita (y su cuenta), cesa y deja igual ---
		MockMultipartFile cambios = excel(
				fila("GOYDELSAC", "40111222", "Luis Goydel Ramos", "luis@goydel.pe", "Jefe", "", "", "SI"),
				fila("GOYDELSAC", "40111227", "Rosa Goydel", "rosa@goydel.pe", "Asistente", "OPE", "", "NO"),
				fila("METRIN", "40111228", "Marco Metrin", "marco@metrin.pe", "", "", "", ""),
				fila("METRIN", "40111231", "Juana Sin Correo", "", "", "", "", ""));
		PersonalCargaDto c = personal.confirmar(cambios, admin);
		assertEquals(1, c.actualizados());
		assertEquals(1, c.desactivados());
		assertEquals(2, c.sinCambios());
		assertEquals("Luis Goydel Ramos", jdbc.queryForObject(
				"select display_name from usuario where user_id = ?", String.class, luis));
		assertEquals("Jefe", jdbc.queryForObject("select cargo from usuario where user_id = ?", String.class, luis));
		assertFalse(jdbc.queryForObject("select active from personal where dni = '40111227'", Boolean.class));
		assertEquals(1, contar("select count(*) from usuario_ceco where user_id = '" + luis + "'"),
				"CECO en blanco = no se cambia");
		assertEquals(List.of("SOLICITANTE"), roles("luis@goydel.pe"), "el Excel no toca roles");

		// --- Sin cambios: no hay nada que confirmar ---
		assertThrows(ApiException.class, () -> personal.confirmar(cambios, admin));

		// --- Correo de la cuenta de otra persona ---
		PersonalCargaDto correoAjeno = personal.validar(excel(
				fila("METRIN", "40111229", "Copia", "luis@goydel.pe", "", "", "", "")), admin);
		assertTrue(correoAjeno.filas().get(0).error().contains("otro usuario"), correoAjeno.filas().toString());

		// --- Alta MANUAL anterior al padron, sin DNI: se reconoce por el correo y se vincula ---
		jdbc.update("""
				insert into usuario (corporate_email, display_name, active, fuente, company_id)
				select 'vieja@metrin.pe', 'Vieja Metrin', true, 'MANUAL', company_id from empresa where code = 'METRIN'""");
		personal.confirmar(excel(fila("METRIN", "40111232", "Vieja Metrin", "vieja@metrin.pe", "", "", "", "")), admin);
		assertEquals(usuario("vieja@metrin.pe"),
				jdbc.queryForObject("select user_id from personal where dni = '40111232'", UUID.class));
		assertEquals("40111232", jdbc.queryForObject(
				"select employee_external_id from usuario where corporate_email = 'vieja@metrin.pe'", String.class));

		// --- Edicion individual de la cuenta: se refleja en su ficha ---
		assertThrows(ApiException.class, () -> adminService.editarUsuario(luis,
				new EditarUsuarioRequest("Luis", "admin@logistica.test", null, null), admin), "correo de otro");
		adminService.editarUsuario(luis,
				new EditarUsuarioRequest("Luis Goydel Diaz", "luis.diaz@goydel.pe", "40111222", "Almacenero"), admin);
		assertEquals("Almacenero", jdbc.queryForObject("select cargo from usuario where user_id = ?", String.class, luis));
		assertEquals("Luis Goydel Diaz", jdbc.queryForObject(
				"select full_name from personal where user_id = ?", String.class, luis));
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
