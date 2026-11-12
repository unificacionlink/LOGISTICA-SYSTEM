package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.documentos.DocumentosService;
import com.tdem.logistica.documentos.PdfCorporativo;
import com.tdem.logistica.documentos.PdfCorporativo.Columna;
import com.tdem.logistica.modules.almacen.CargaMasivaService;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 7 (parte 2) contra Postgres real: carga masiva de ingresos (RF-031)
 * y PDFs corporativos (RF-038/082/083).
 *
 * Escenario: REQ de 10 u. y OC por las 10 u.; se reciben 6 por Excel, se
 * despachan en una NI y se generan los cinco PDFs.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class DocumentosYCargaMasivaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private CargaMasivaService cargaMasivaService;
	@Autowired private DespachoService despachoService;
	@Autowired private DocumentosService documentosService;

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
	void cargaMasivaYDocumentosPdf() throws IOException {
		UUID logisticaId = usuario("logistica@logistica.test");
		UsuarioPrincipal logistica = new UsuarioPrincipal(logisticaId, List.of("LOGISTICA"));
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		Fixture f = crearFixture(logisticaId);

		// --- Plantilla: una fila por item pendiente, con su ID ---
		assertThrows(ApiException.class, () -> cargaMasivaService.plantilla(solicitante));
		byte[] plantilla = cargaMasivaService.plantilla(logistica);
		int filaItem;
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(plantilla))) {
			Sheet hoja = wb.getSheet("INGRESOS_PENDIENTES");
			filaItem = buscarFila(hoja, f.orderItemId);
			Row row = hoja.getRow(filaItem);
			assertEquals("OC-TEST-DOC", row.getCell(0).getStringCellValue());
			assertEquals(10.0, row.getCell(9).getNumericCellValue(), "pendiente");
			assertEquals("CONFORME", row.getCell(11).getStringCellValue());
		}

		// --- Archivo con errores: item repetido y cantidad invalida. No se aplica nada. ---
		byte[] conErrores = editar(plantilla, hoja -> {
			Row ok = hoja.getRow(filaItem);
			ok.getCell(10).setCellValue(6);
			ok.getCell(13).setCellValue("T001");
			ok.getCell(14).setCellValue("123");
			ok.getCell(15).setCellValue("15/09/2026");
			Row repetida = hoja.createRow(hoja.getLastRowNum() + 1);
			repetida.createCell(10).setCellValue(1);
			repetida.createCell(16).setCellValue(f.orderItemId.toString());
			Row invalida = hoja.createRow(hoja.getLastRowNum() + 1);
			invalida.createCell(10).setCellValue("seis");
			invalida.createCell(16).setCellValue(UUID.randomUUID().toString());
		});
		CargaMasivaDto v = cargaMasivaService.validar(archivo(conErrores), logistica);
		assertEquals(1, v.validas());
		assertEquals(2, v.conError());
		assertTrue(v.filas().get(1).error().contains("repetido"), v.filas().get(1).error());
		assertNotNull(v.filas().get(2).error());
		ApiException rechazo = assertThrows(ApiException.class,
				() -> cargaMasivaService.confirmar(archivo(conErrores), logistica));
		assertTrue(rechazo.getMessage().contains("2 fila(s) con error"), rechazo.getMessage());
		assertEquals(0, recibido(f).signum(), "con errores no entra nada");

		// --- Archivo corregido: 6 u. con guia -> recepcion CARGA_MASIVA ---
		byte[] correcto = editar(plantilla, hoja -> {
			Row ok = hoja.getRow(filaItem);
			ok.getCell(10).setCellValue(6);
			ok.getCell(12).setCellValue("llego bien");
			ok.getCell(13).setCellValue("t001");
			ok.getCell(14).setCellValue("123");
			ok.getCell(15).setCellValue("2026-09-15");
		});
		CargaMasivaDto aplicada = cargaMasivaService.confirmar(archivo(correcto), logistica);
		assertTrue(aplicada.aplicada());
		assertEquals(1, aplicada.validas());
		assertEquals(1, aplicada.ordenes());
		assertEquals(0, recibido(f).compareTo(new BigDecimal("6")));
		assertEquals("RECEPCION_PARCIAL", jdbc.queryForObject(
				"select status from orden_abastecimiento where order_id = ?", String.class, f.orderId));
		assertEquals("CARGA_MASIVA", jdbc.queryForObject(
				"select registration_source from recepcion where order_id = ?", String.class, f.orderId));
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from guia_remision where series = 'T001' and number = '123'", Integer.class));

		// --- Subir el mismo archivo otra vez: la guia ya existe y 6 > pendiente 4 ---
		CargaMasivaDto repetido = cargaMasivaService.validar(archivo(correcto), logistica);
		assertEquals(1, repetido.conError());
		assertTrue(repetido.filas().get(0).error().contains("Supera lo pendiente (4)"), repetido.filas().get(0).error());

		// --- Despacho de las 6 u. + envio, para tener NI y Tracking con datos ---
		NotaInventarioDetalleDto ni = despachoService.crearNotaInventario(f.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(f.requestItemId, new BigDecimal("6"))), 2, null,
						"Entregar en recepcion"), logistica);
		despachoService.registrarEnvio(ni.nota().inventoryNoteId(),
				new RegistrarEnvioRequest("AGENCIA", "Shalom", "SH-777", 2, null, "PEN",
						new BigDecimal("40"), null, null, null), logistica);

		// --- PDFs (RF-038/082/083) ---
		String req = texto(documentosService.requerimiento(f.requestId, solicitante).contenido());
		assertTrue(req.contains("REQUERIMIENTO") && req.contains("REQ-TEST-DOC") && req.contains("AC01001-00"), req);
		assertTrue(req.contains("PLT-REQ v1") && req.contains("Página 1 de 1"), req);

		String rfq = texto(documentosService.rfq(f.rfqId, null, logistica).contenido());
		assertTrue(rfq.contains("SOLICITUD DE COTIZACIÓN") && rfq.contains("RFQ-TEST-DOC") && rfq.contains("20111222333"), rfq);

		String oc = texto(documentosService.orden(f.orderId, logistica).contenido());
		assertTrue(oc.contains("ORDEN DE COMPRA") && oc.contains("OC-TEST-DOC") && oc.contains("50.00"), oc);

		DocumentosService.Pdf pdfNi = documentosService.notaInventario(ni.nota().inventoryNoteId(), logistica);
		assertEquals("NI-1-TDEMSRL.pdf", pdfNi.nombre());
		String textoNi = texto(pdfNi.contenido());
		assertTrue(textoNi.contains("NOTA DE INVENTARIO") && textoNi.contains("REQ-TEST-DOC"), textoNi);
		assertTrue(textoNi.contains("Shalom") && textoNi.contains("SH-777") && textoNi.contains("Entregar en recepcion"), textoNi);
		assertTrue(textoNi.contains("(ADM)"), "destino con codigo de CECO: " + textoNi);

		String trk = texto(documentosService.tracking(f.requestId, solicitante).contenido());
		assertTrue(trk.contains("TRACKING DE REQUERIMIENTO") && trk.contains("NI-1-TDEMSRL"), trk);
		assertTrue(trk.contains("0 de 10 unidades (0%)"), "el envio sigue en transito: " + trk);

		// Permisos: los mismos de cada pantalla.
		UsuarioPrincipal ajeno = new UsuarioPrincipal(UUID.randomUUID(), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> documentosService.requerimiento(f.requestId, ajeno));
		assertThrows(ApiException.class, () -> documentosService.notaInventario(ni.nota().inventoryNoteId(), solicitante));
		assertThrows(ApiException.class, () -> documentosService.orden(f.orderId, solicitante));
	}

	@Test
	void pdfSaltaDePaginaYToleraCaracteresFueraDeWinAnsi() throws IOException {
		PdfCorporativo pdf = new PdfCorporativo("PRUEBA", "DOC-1", "GOYDELSAC", "PLT-X v1", "pie");
		List<List<String>> filas = new ArrayList<>();
		for (int i = 1; i <= 120; i++) {
			filas.add(List.of(String.valueOf(i), "Producto " + i + " → con flecha ✓ y ñandú", "x".repeat(90)));
		}
		pdf.tabla(List.of(Columna.numero("#", 1), Columna.texto("Producto", 4), Columna.texto("Nota", 2)), filas);
		byte[] bytes = pdf.terminar();
		try (PDDocument doc = Loader.loadPDF(bytes)) {
			assertTrue(doc.getNumberOfPages() >= 3, "paginas: " + doc.getNumberOfPages());
			String t = new PDFTextStripper().getText(doc);
			assertTrue(t.contains("Producto 120 -> con flecha ? y ñandú"), t);
			assertTrue(t.contains("Página 2 de " + doc.getNumberOfPages()));
			assertTrue(t.contains("GOYDELSAC"), "sin logo, la cabecera lleva el codigo de la empresa");
		}
	}

	// ------------------------------------------------------------------

	private static String texto(byte[] pdf) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	private static MockMultipartFile archivo(byte[] contenido) {
		return new MockMultipartFile("file", "ingresos.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
	}

	private static byte[] editar(byte[] xlsx, Consumer<Sheet> cambios) throws IOException {
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cambios.accept(wb.getSheet("INGRESOS_PENDIENTES"));
			wb.write(out);
			return out.toByteArray();
		}
	}

	private static int buscarFila(Sheet hoja, UUID orderItemId) {
		for (Row row : hoja) {
			if (row.getRowNum() > 0 && orderItemId.toString().equals(row.getCell(16).getStringCellValue())) {
				return row.getRowNum();
			}
		}
		throw new AssertionError("El item no esta en la plantilla");
	}

	private BigDecimal recibido(Fixture f) {
		return jdbc.queryForObject("select coalesce(sum(qty_received), 0) from recepcion_item where order_item_id = ?",
				BigDecimal.class, f.orderItemId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}

	private record Fixture(UUID requestId, UUID requestItemId, UUID rfqId, UUID orderId, UUID orderItemId) {
	}

	/** REQ de 10 u. en atencion con una OC emitida por las 10 u. a S/ 5. */
	private Fixture crearFixture(UUID logisticaId) {
		UUID solicitanteId = usuario("solicitante@logistica.test");
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = 'AC01001'", UUID.class);
		UUID variante = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);

		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values ('REQ-TEST-DOC', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba documentos', 'EN_ATENCION')
				returning request_id""", UUID.class, empresa, ceco, solicitanteId);
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, 10) returning request_item_id""", UUID.class, req, producto, variante, uom);
		UUID proveedor = jdbc.queryForObject("""
				insert into proveedor (ruc, legal_name) values ('20111222333', 'Proveedor Documentos SAC')
				returning supplier_id""", UUID.class);
		UUID rfq = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-TEST-DOC', ?, ?, ?, 'CERRADA', ?) returning rfq_id""", UUID.class, empresa, req, proveedor, logisticaId);
		UUID rfqItem = jdbc.queryForObject("""
				insert into rfq_item (rfq_id, request_item_id, product_id, variant_id, uom_id, qty)
				values (?, ?, ?, ?, ?, 10) returning rfq_item_id""", UUID.class, rfq, item, producto, variante, uom);
		UUID cotizacion = jdbc.queryForObject("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
				values (?, ?, 'PEN', 50, 50, 'SELECCIONADA') returning quotation_id""", UUID.class, rfq, proveedor);
		jdbc.update("""
				insert into cotizacion_item (quotation_id, rfq_item_id, product_id, variant_id, qty, unit_cost, line_total)
				values (?, ?, ?, ?, 10, 5, 50)""", cotizacion, rfqItem, producto, variante);
		UUID orden = jdbc.queryForObject("""
				insert into orden_abastecimiento (order_code, company_id, supplier_id, quotation_id, payment_condition,
				  delivery_mode, currency_code, subtotal, total, status, created_by)
				values ('OC-TEST-DOC', ?, ?, ?, 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', 50, 50, 'EMITIDA', ?)
				returning order_id""", UUID.class, empresa, proveedor, cotizacion, logisticaId);
		UUID ordenItem = jdbc.queryForObject("""
				insert into orden_item (order_id, request_item_id, product_id, variant_id, description, uom_id,
				  qty_ordered, unit_cost, line_subtotal, line_total)
				values (?, ?, ?, ?, 'Producto de prueba', ?, 10, 5, 50, 50) returning order_item_id""",
				UUID.class, orden, item, producto, variante, uom);
		return new Fixture(req, item, rfq, orden, ordenItem);
	}
}
