package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.cajachica.CompraCajaChicaService;
import com.tdem.logistica.modules.cajachica.dto.CompraCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.ConsolidadoCajaChicaDto;
import com.tdem.logistica.modules.cajachica.dto.RegistrarCompraCajaChicaRequest;
import com.tdem.logistica.modules.cajachica.dto.RegistrarCompraCajaChicaRequest.Linea;
import com.tdem.logistica.modules.identidad.AdminService;
import com.tdem.logistica.modules.repositorio.RepositorioService;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DEC-L092: caja chica como modulo. El solicitante registra lo que compro con la
 * caja chica de su CECO, con la factura obligatoria (archivo o enlace), sin
 * requerimiento ni revision; Logistica, Gerencia y el aprobador del CECO ven el
 * consolidado.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class CompraCajaChicaTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private CompraCajaChicaService cajaChica;
	@Autowired private AdminService admin;
	@Autowired private RepositorioService repositorio;
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
	void elSolicitanteRegistraConFacturaYLogisticaVeElConsolidado() {
		when(storage.subir(anyString(), any(), anyString())).thenAnswer(inv -> "https://storage.test/" + inv.getArgument(0));
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UUID adm = ceco("TDEMSRL", "ADM");
		LocalDate hoy = LocalDate.now(LIMA);
		UUID pilas = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = 'AC01001-00'",
				UUID.class);

		// Con la foto de la factura: una linea del catalogo y una libre.
		CompraCajaChicaDto c1 = cajaChica.registrar(request(adm, hoy, "20512345678", "FACTURA", "F001", "123", null,
				List.of(new Linea(pilas, null, new BigDecimal("2"), new BigDecimal("12.50")),
						new Linea(null, "Movilidad a la obra", BigDecimal.ONE, new BigDecimal("15")))),
				factura(), solicitante);
		assertEquals("REGISTRADA", c1.estado());
		assertEquals(0, c1.total().compareTo(new BigDecimal("40.00")));
		assertNotNull(c1.facturaArchivoUrl());
		assertEquals("AC01001-00", c1.lineas().stream().filter(l -> l.sku() != null).findFirst().orElseThrow().sku());

		// Boleta en PDF.
		CompraCajaChicaDto c2 = cajaChica.registrar(request(adm, hoy, null, "BOLETA", "B001", "77", null,
				List.of(new Linea(null, "Cinta de embalaje", new BigDecimal("3"), new BigDecimal("5")))),
				new MockMultipartFile("file", "boleta.pdf", "application/pdf", new byte[] {1, 2, 3}), solicitante);
		assertNotNull(c2.facturaArchivoUrl());

		// Validaciones.
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, null, "BOLETA", "B001", "78", null,
				List.of(new Linea(null, "Sin factura", BigDecimal.ONE, BigDecimal.TEN))), null, solicitante), "factura obligatoria");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, null, "BOLETA", "B001", "79",
				"https://drive.test/boleta.pdf", List.of(new Linea(null, "Con enlace", BigDecimal.ONE, BigDecimal.TEN))),
				null, solicitante), "DEC-L099: sin enlace");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, null, "BOLETA", "B001", "80", null,
				List.of(new Linea(null, "Webp", BigDecimal.ONE, BigDecimal.TEN))),
				new MockMultipartFile("file", "f.webp", "image/webp", new byte[] {1}), solicitante), "solo PDF, PNG o JPG");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, null, "FACTURA", "F001", "200",
				null, List.of(new Linea(null, "x", BigDecimal.ONE, BigDecimal.TEN))), factura(), solicitante),
				"una factura lleva RUC");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, "20512345678", "FACTURA", "F001", "123",
				null, List.of(new Linea(null, "Repetida", BigDecimal.ONE, BigDecimal.TEN))), factura(), solicitante),
				"comprobante repetido");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy.plusDays(1), null, "BOLETA", "B001", "1",
				null, List.of(new Linea(null, "Futura", BigDecimal.ONE, BigDecimal.TEN))), factura(), solicitante));
		UUID ajeno = ceco("GOYDELSAC", "CCH-G1");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(ajeno, hoy, null, "BOLETA", "B001", "9",
				null, List.of(new Linea(null, "Ajeno", BigDecimal.ONE, BigDecimal.TEN))), factura(), solicitante),
				"solo sus CECOs");
		assertThrows(ApiException.class, () -> cajaChica.registrar(request(adm, hoy, null, "TICKET", null, "10",
				null, List.of(new Linea(null, "Ticket", BigDecimal.ONE, BigDecimal.TEN))), factura(), solicitante),
				"solo boleta o factura");
		assertEquals(2, cajaChica.mias(solicitante).size());

		// Consolidado: Logistica ve todo; el aprobador, solo los CECOs que aprueba o suple.
		String mes = hoy.toString().substring(0, 7);
		ConsolidadoCajaChicaDto todo = cajaChica.consolidado(mes, mes, null, null, logistica);
		assertEquals(0, todo.total().compareTo(new BigDecimal("55.00")));
		assertEquals(2, todo.compras());
		assertEquals("ADM", todo.porCeco().get(0).clave());
		assertEquals(1, todo.porSolicitante().size());

		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		assertEquals(0, cajaChica.consolidado(mes, mes, null, null, aprobador).compras(), "no aprueba ADM");
		admin.asignarCecosAprobados(aprobador.userId(), List.of(adm), principal("admin@logistica.test", "ADMIN"));
		assertEquals(2, cajaChica.consolidado(mes, mes, null, null, aprobador).compras());
		assertThrows(ApiException.class, () -> cajaChica.consolidado(mes, mes, null, null, solicitante));

		// Anular: sale del total y queda contada aparte.
		assertThrows(ApiException.class, () -> cajaChica.anular(c2.id(), "x", solicitante), "motivo corto");
		assertEquals("ANULADA", cajaChica.anular(c2.id(), "Boleta duplicada en otra rendición", solicitante).estado());
		ConsolidadoCajaChicaDto despues = cajaChica.consolidado(mes, mes, null, null, logistica);
		assertEquals(0, despues.total().compareTo(new BigDecimal("40.00")));
		assertEquals(1, despues.anuladas());
		assertEquals(2, cajaChica.paraExcel(mes, mes, null, null, logistica).size());
		assertThrows(ApiException.class, () -> cajaChica.consolidado("2026-13", mes, null, null, logistica));

		// La factura queda en el repositorio de documentos (DEC-L091).
		assertEquals(2, repositorio.listar("COMPROBANTE_CAJA_CHICA", null, mes, null, logistica).stream()
				.filter(d -> d.url() != null).count());
	}

	// ------------------------------------------------------------------

	private static RegistrarCompraCajaChicaRequest request(UUID ceco, LocalDate fecha, String ruc, String tipo, String serie,
			String numero, String url, List<Linea> lineas) {
		return new RegistrarCompraCajaChicaRequest(ceco, fecha, ruc, "Ferretería El Puerto", tipo, serie, numero, url, null,
				lineas);
	}

	private static MockMultipartFile factura() {
		return new MockMultipartFile("file", "factura.jpg", "image/jpeg", new byte[] {1, 2, 3});
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(jdbc.queryForObject("select user_id from usuario where corporate_email = ?",
				UUID.class, correo), List.of(rol));
	}

	private UUID ceco(String empresa, String code) {
		jdbc.update("""
				insert into ceco (company_id, code, name) select company_id, ?, 'Proyecto ' || ? from empresa where code = ?
				on conflict (company_id, code) do nothing""", code, code, empresa);
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id where e.code = ? and c.code = ?""",
				UUID.class, empresa, code);
	}
}
