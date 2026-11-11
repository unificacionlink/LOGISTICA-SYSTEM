package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.stock.CargaStockService;
import com.tdem.logistica.modules.stock.StockService;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.ss.usermodel.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DEC-L045: almacen central unico (TDEM), ajustes con motivo, stock minimo, kardex e inventario por Excel. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class StockCentralTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private StockService stock;
	@Autowired private CargaStockService carga;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private RequerimientoService requerimientos;

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
	void unSoloAlmacenCentralDelGrupo() {
		assertEquals("TDEMSRL", jdbc.queryForObject("""
				select e.code from almacen a join empresa e on e.company_id = a.company_id where a.is_group_central""",
				String.class));
		assertEquals(0, jdbc.queryForObject("""
				select count(*) from almacen a join empresa e on e.company_id = a.company_id
				where e.code in ('GOYDELSAC', 'METRIN') and a.active""", Integer.class), "los vacios quedan inactivos");
	}

	@Test
	void ajusteMinimoKardexEInventarioPorExcel() throws IOException {
		UsuarioPrincipal logistica = logistica();
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		UUID ac1 = variante("AC01005-00");
		UUID ac2 = variante("AC01006-00");

		assertThrows(ApiException.class, () -> stock.listarSaldos(solicitante), "solo Logistica/Admin");
		assertTrue(stock.listarSaldos(logistica).size() > 500, "trae todo el catalogo, tenga o no stock");

		// --- Ajuste manual con motivo; no deja el fisico en negativo ---
		SaldoStockDto s = stock.registrarAjuste(new AjusteStockRequest(ac1, new BigDecimal("10"), "Saldo inicial"), logistica);
		assertEquals(0, s.fisico().compareTo(BigDecimal.TEN));
		assertThrows(ApiException.class, () -> stock.registrarAjuste(
				new AjusteStockRequest(ac1, new BigDecimal("-11"), "Merma"), logistica));
		assertThrows(ApiException.class, () -> stock.registrarAjuste(
				new AjusteStockRequest(ac1, BigDecimal.ZERO, "Nada"), logistica));

		// --- Stock minimo ---
		assertEquals(0, stock.fijarMinimo(ac1, new BigDecimal("15"), logistica).minimo().compareTo(new BigDecimal("15")));
		assertNull(stock.fijarMinimo(ac2, null, logistica).minimo());

		// --- Inventario por Excel: se escribe lo contado ---
		byte[] plantilla = carga.plantilla(logistica);
		MockMultipartFile archivo = editar(plantilla, Map.of(
				"AC01005-00", new String[] {"12", null},       // +2; el minimo 15 se deja como esta
				"AC01006-00", new String[] {"5", "3"}));       // +5 y minimo 3
		CargaStockDto v = carga.validar(archivo, logistica);
		assertEquals(0, v.conError(), v.detalle().toString());
		assertEquals(2, v.ajustes());
		assertEquals(1, v.minimos());
		assertFalse(v.aplicada());
		assertEquals(0, stock.listarSaldos(logistica).stream().filter(x -> x.variantId().equals(ac2)).findFirst()
				.orElseThrow().fisico().signum(), "validar no escribe");

		CargaStockDto r = carga.confirmar(archivo, logistica);
		assertTrue(r.aplicada());
		assertEquals("INV-1", r.codigo());
		SaldoStockDto s1 = saldo(ac1, logistica);
		SaldoStockDto s2 = saldo(ac2, logistica);
		assertEquals(0, s1.fisico().compareTo(new BigDecimal("12")));
		assertEquals(0, s1.minimo().compareTo(new BigDecimal("15")));
		assertEquals(0, s2.fisico().compareTo(new BigDecimal("5")));
		assertEquals(0, s2.minimo().compareTo(new BigDecimal("3")));
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from log_cambios where entidad = 'CARGA_STOCK' and accion = 'APLICAR'", Integer.class));

		// El mismo archivo otra vez: ya no hay diferencias, no duplica.
		assertThrows(ApiException.class, () -> carga.confirmar(archivo, logistica));

		// --- Errores: codigo inexistente, negativo, repetido ---
		CargaStockDto malo = carga.validar(editar(plantilla, Map.of("AC01005-00", new String[] {"-1", null})), logistica);
		assertEquals(1, malo.conError());
		CargaStockDto decimal = carga.validar(editar(plantilla, Map.of("AC01005-00", new String[] {"2.5", null})), logistica);
		assertEquals(1, decimal.conError(), "las cantidades son enteras");

		// --- Kardex: del mas reciente al mas antiguo, con saldo acumulado ---
		KardexDto k = stock.kardex(ac1, logistica);
		assertEquals(2, k.movimientos().size());
		KardexDto.Movimiento ultimo = k.movimientos().get(0);
		assertEquals("CARGA_STOCK", ultimo.origen());
		assertEquals("INV-1", ultimo.referencia());
		assertEquals(0, ultimo.cantidad().compareTo(new BigDecimal("2")));
		assertEquals(0, ultimo.saldo().compareTo(new BigDecimal("12")));
		assertEquals("Saldo inicial", k.movimientos().get(1).motivo());
	}

	@Test
	void requerimientoDeGoydelApartaStockDeTdem() {
		UsuarioPrincipal logistica = logistica();
		UUID variante = variante("AC01007-00");
		stock.registrarAjuste(new AjusteStockRequest(variante, new BigDecimal("8"), "Saldo inicial"), logistica);

		UUID goydel = jdbc.queryForObject("select company_id from empresa where code = 'GOYDELSAC'", UUID.class);
		UUID ceco = jdbc.queryForObject("""
				insert into ceco (company_id, code, name, active) values (?, 'G01', 'GOYDEL OPERACIONES', true)
				returning ceco_id""", UUID.class, goydel);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				values ('REQ-GOYDEL-1', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'NORMAL')
				returning request_id""", UUID.class, goydel, ceco, usuario("solicitante@logistica.test"));
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, 5
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.variant_id = ? returning request_item_id""", UUID.class, req, variante);
		UUID aprobacion = jdbc.queryForObject(
				"insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id",
				UUID.class, req);

		aprobacionService.decidir(aprobacion, new DecidirAprobacionRequest("APROBADO", null, null),
				new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN")));

		assertEquals("TDEMSRL", jdbc.queryForObject("""
				select e.code from reserva_stock r join almacen a on a.warehouse_id = r.warehouse_id
				join empresa e on e.company_id = a.company_id where r.request_item_id = ?""", String.class, item));
		assertEquals(0, new BigDecimal("3").compareTo(saldo(variante, logistica).disponible()));

		// Quien pide ve el disponible del almacen central al armar su requerimiento: 8 - 5 reservados.
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		var stock = requerimientos.stockDisponible(solicitante);
		assertEquals(0, new BigDecimal("3").compareTo(stock.get(variante)));
		assertTrue(stock.values().stream().allMatch(q -> q.signum() > 0));
	}

	// ------------------------------------------------------------------

	/** Escribe "Stock contado" y "Stock minimo" en las filas indicadas de la plantilla. */
	private static MockMultipartFile editar(byte[] plantilla, Map<String, String[]> valores) throws IOException {
		try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(plantilla));
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet hoja = wb.getSheetAt(0);
			for (int r = 1; r <= hoja.getLastRowNum(); r++) {
				Row row = hoja.getRow(r);
				String[] v = valores.get(row.getCell(0).getStringCellValue());
				if (v == null) {
					continue;
				}
				if (v[0] != null) {
					row.getCell(8).setCellValue(Double.parseDouble(v[0]));
				}
				if (v[1] != null) {
					row.getCell(9).setCellValue(Double.parseDouble(v[1]));
				}
			}
			wb.write(out);
			return new MockMultipartFile("file", "inventario.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
		}
	}

	private SaldoStockDto saldo(UUID variante, UsuarioPrincipal p) {
		return stock.listarSaldos(p).stream().filter(x -> x.variantId().equals(variante)).findFirst().orElseThrow();
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private UUID variante(String codigo) {
		return jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?", UUID.class, codigo);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
