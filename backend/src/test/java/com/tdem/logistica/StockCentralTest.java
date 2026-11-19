package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.entity.NecesidadCompra;
import com.tdem.logistica.entity.ReservaStock;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.stock.CargaStockService;
import com.tdem.logistica.modules.stock.StockService;
import com.tdem.logistica.modules.stock.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
	@Autowired private EntityManagerFactory emf;

	@DynamicPropertySource
	static void bd(DynamicPropertyRegistry registry) throws IOException, SQLException {
		pg = BdRrhhSimulada.iniciar();
		registry.add("spring.datasource.url", () -> BdRrhhSimulada.jdbcUrl(pg));
		registry.add("spring.datasource.username", () -> BdRrhhSimulada.USUARIO_APP);
		registry.add("spring.datasource.password", () -> BdRrhhSimulada.PASSWORD_APP);
		registry.add("app.jwt.secret", () -> "clave-de-prueba-solo-para-tests-0123456789-abcdefghijklmnop");
		// Para contar sentencias en la aprobacion por lotes (DEC-L074).
		registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
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
				values ('REQ-GOYDEL-1', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'APROVISIONAMIENTO')
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

		// Logistica ve el disponible del almacen central: 8 - 5 reservados.
		var stock = requerimientos.stockDisponible(logistica);
		assertEquals(0, new BigDecimal("3").compareTo(stock.get(variante)));
		assertTrue(stock.values().stream().allMatch(q -> q.signum() > 0));

		// Quien pide solo sabe que hay stock, no cuanto (DEC-L059).
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		var soloEstado = requerimientos.stockDisponible(solicitante);
		assertEquals(stock.keySet(), soloEstado.keySet());
		assertTrue(soloEstado.values().stream().allMatch(q -> q.compareTo(BigDecimal.ONE) == 0));
	}


	/**
	 * DEC-L074: aprobar un REQ de 10 items reserva y consolida por conjuntos.
	 * Dos items del mismo SKU: el segundo ve lo que aparto el primero. Ninguna
	 * alta hace el SELECT previo de merge y las sentencias no crecen por item.
	 */
	@Test
	void aprobarDiezItemsReservaYConsolidaPorLotes() {
		UsuarioPrincipal logistica = logistica();
		List<UUID> variantes = jdbc.queryForList("""
				select v.variant_id from producto_variante v join producto p on p.product_id = v.product_id
				where p.active and v.internal_variant_code not in ('AC01005-00', 'AC01006-00', 'AC01007-00')
				order by v.internal_variant_code limit 9""", UUID.class);
		assertEquals(9, variantes.size());
		UUID conStock = variantes.get(0);
		stock.registrarAjuste(new AjusteStockRequest(conStock, new BigDecimal("8"), "Saldo inicial"), logistica);
		stock.registrarAjuste(new AjusteStockRequest(variantes.get(1), new BigDecimal("2"), "Saldo inicial"), logistica);

		UUID tdem = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and active limit 1", UUID.class, tdem);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				values ('REQ-LOTE-10', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'APROVISIONAMIENTO')
				returning request_id""", UUID.class, tdem, ceco, usuario("solicitante@logistica.test"));
		// 10 items: el primer SKU dos veces (5 + 5 con 8 en stock), el resto una vez de 4.
		List<UUID> items = new java.util.ArrayList<>();
		for (int i = 0; i < 10; i++) {
			UUID v = i < 2 ? conStock : variantes.get(i - 1);
			items.add(jdbc.queryForObject("""
					insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
					select ?, v.product_id, v.variant_id, p.base_uom_id, ?
					from producto_variante v join producto p on p.product_id = v.product_id
					where v.variant_id = ? returning request_item_id""", UUID.class, req, i < 2 ? 5 : 4, v));
		}
		UUID aprobacion = jdbc.queryForObject(
				"insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id",
				UUID.class, req);

		Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
		stats.clear();
		aprobacionService.decidir(aprobacion, new DecidirAprobacionRequest("APROBADO", null, null),
				new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN")));

		// Reservas: 5 + 3 del SKU con 8, 2 del SKU con 2.
		assertEquals(0, new BigDecimal("5").compareTo(reservado(items.get(0))));
		assertEquals(0, new BigDecimal("3").compareTo(reservado(items.get(1))));
		assertEquals(0, new BigDecimal("2").compareTo(reservado(items.get(2))));
		assertEquals(0, saldo(conStock, logistica).disponible().signum(), "no se aparta mas de lo que hay");
		// Necesidades: 2 del segundo item + 2 del SKU con 2 + 4 de cada uno de los otros 7 = 32.
		assertEquals(0, new BigDecimal("32").compareTo(jdbc.queryForObject("""
				select sum(n.qty) from necesidad_compra n join requerimiento_item i on i.request_item_id = n.request_item_id
				where i.request_id = ?""", BigDecimal.class, req)));
		// Cada necesidad quedo en el consolidado abierto de su SKU, con su total.
		assertEquals(0, jdbc.queryForObject("""
				select count(*) from necesidad_compra n join requerimiento_item i on i.request_item_id = n.request_item_id
				where i.request_id = ? and (n.consolidation_id is null or n.status <> 'COMPRA')""", Integer.class, req));
		assertEquals(0, jdbc.queryForObject("""
				select count(*) from consolidacion c
				where c.status = 'ABIERTA' and c.total_qty <> (
				  select coalesce(sum(n.qty), 0) from necesidad_compra n where n.consolidation_id = c.consolidation_id)""",
				Integer.class));

		// Sin el SELECT de merge antes de cada alta.
		assertEquals(0, stats.getEntityStatistics(ReservaStock.class.getName()).getLoadCount());
		assertEquals(0, stats.getEntityStatistics(NecesidadCompra.class.getName()).getLoadCount());
		assertTrue(stats.getPrepareStatementCount() <= 25,
				"sentencias JPA de la aprobacion: " + stats.getPrepareStatementCount());
	}

	private BigDecimal reservado(UUID item) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock where request_item_id = ?",
				BigDecimal.class, item);
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
