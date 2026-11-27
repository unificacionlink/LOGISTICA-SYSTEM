package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.reportes.ReporteObservacionService;
import com.tdem.logistica.modules.reportes.dto.ReporteObservacionDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.ss.usermodel.Sheet;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DEC-L104: cualquier usuario reporta una observacion senalando la zona del
 * esqueleto; solo ADMIN la ve, la responde y la exporta a Excel.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ReporteObservacionTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private ReporteObservacionService reportes;
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
	void elUsuarioReportaYElAdministradorRevisaYExporta() throws IOException {
		when(storage.privadoConfigurado()).thenReturn(true);
		when(storage.subirPrivado(anyString(), any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal admin = principal("admin@logistica.test", "ADMIN");

		ReporteObservacionDto.Creado conZona = reportes.reportar(new ReporteObservacionService.Nuevo("Compras",
				"Orden de compra", "/compras/ordenes/1", "Tarjeta «Pagos» › Botón «Registrar pago»",
				"  El botón no responde  ", "1440x900"),
				new MockMultipartFile("captura", "c.png", "image/png", png()), "Prueba/1.0", solicitante);
		ReporteObservacionDto.Creado general = reportes.reportar(new ReporteObservacionService.Nuevo("Inicio",
				null, "/inicio", null, "Tarda en cargar", null), null, null, solicitante);
		assertEquals(conZona.numero() + 1, general.numero());

		List<ReporteObservacionDto.Propio> mios = reportes.mios(solicitante);
		assertEquals(general.id(), mios.get(0).id());
		assertEquals("PENDIENTE", mios.get(0).estado());

		// Solo ADMIN revisa.
		assertThrows(ApiException.class, () -> reportes.listar(null, null, 0, solicitante));
		assertThrows(ApiException.class, () -> reportes.revisar(conZona.id(), "RESUELTO", null, solicitante));

		ReporteObservacionDto.Pagina pagina = reportes.listar("ABIERTOS", "registrar pago", 0, admin);
		assertEquals(1, pagina.filas().size());
		ReporteObservacionDto.Fila fila = pagina.filas().get(0);
		assertEquals("El botón no responde", fila.descripcion());
		assertTrue(fila.tieneCaptura());
		assertTrue(fila.roles().contains("SOLICITANTE"));
		assertTrue(pagina.conteo().pendientes() >= 2);

		ReporteObservacionDto.Fila revisada = reportes.revisar(conZona.id(), "resuelto", "Corregido en 0.82.0", admin);
		assertEquals("RESUELTO", revisada.estado());
		assertEquals("Corregido en 0.82.0", revisada.respuesta());
		assertNotNull(revisada.revisadoEn());
		assertThrows(ApiException.class, () -> reportes.revisar(conZona.id(), "OTRO", null, admin));
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'REPORTE_OBSERVACION' and entidad_id = ?""",
				Integer.class, conZona.id()));

		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(reportes.excel(null, null, admin)))) {
			Sheet hoja = wb.getSheet("OBSERVACIONES");
			assertEquals("MÓDULO / PANTALLA", hoja.getRow(0).getCell(4).getStringCellValue());
			assertEquals("Compras · Orden de compra", hoja.getRow(1).getCell(4).getStringCellValue());
			assertEquals("Resuelto", hoja.getRow(1).getCell(7).getStringCellValue());
			assertEquals("Toda la pantalla", hoja.getRow(2).getCell(5).getStringCellValue());
		}
	}

	@Test
	void validaDescripcionYCaptura() {
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		ReporteObservacionService.Nuevo vacio = new ReporteObservacionService.Nuevo("Stock", null, "/stock", null,
				"   ", null);
		assertThrows(ApiException.class, () -> reportes.reportar(vacio, null, null, logistica));
		ReporteObservacionService.Nuevo ok = new ReporteObservacionService.Nuevo("Stock", null, "/stock", null,
				"Falta columna", null);
		assertThrows(ApiException.class, () -> reportes.reportar(ok,
				new MockMultipartFile("captura", "c.pdf", "application/pdf", new byte[]{1, 2}), null, logistica));
		assertThrows(ApiException.class, () -> reportes.reportar(ok,
				new MockMultipartFile("captura", "c.png", "image/png", new byte[]{1, 2, 3}), null, logistica));
	}

	private static byte[] png() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(jdbc.queryForObject("select user_id from usuario where corporate_email = ?",
				UUID.class, correo), List.of(rol));
	}
}
