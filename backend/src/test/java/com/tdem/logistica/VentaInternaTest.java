package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.dto.CrearNotaRequest;
import com.tdem.logistica.modules.almacen.dto.DespachoItemInput;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.dashboard.DashboardController;
import com.tdem.logistica.modules.dashboard.VentasDto;
import com.tdem.logistica.modules.dashboard.VentasService;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEC-L086: TDEM es la empresa central. Lo que despacha a un proyecto de GOYDEL
 * lo vende al precio de venta de GOYDEL: la Nota de Inventario sale de TDEM, el
 * precio queda congelado en la linea y aparece en el reporte de ventas.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class VentaInternaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private DespachoService despacho;
	@Autowired private VentasService ventas;
	@Autowired private DashboardController dashboard;

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
	void despachoAGoydelEsVentaDeTdemAlPrecioDeVentaDeGoydel() throws IOException {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UsuarioPrincipal gerencia = new UsuarioPrincipal(usuario("gerencia@logistica.test"), List.of("GERENCIA"));
		precioVenta("GOYDELSAC", "AC01002", "25.50");
		precioVenta("TDEMSRL", "AC01002", "99.00");
		Despachable goydel = requerimientoConStock("GOYDELSAC", "VI-G1", "AC01002-00", 4);
		Despachable tdem = requerimientoConStock("TDEMSRL", "ADM", "AC01002-00", 2);

		NotaInventarioDetalleDto ni = despacho.crearNotaInventario(goydel.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(goydel.itemId, new BigDecimal("4"))), 1, null, null),
				logistica);
		assertTrue(ni.nota().inventoryNoteCode().matches("NI-\\d+-TDEMSRL"), "la emite TDEM: " + ni.nota().inventoryNoteCode());
		assertEquals("GOYDELSAC", ni.nota().empresaVentaCode());
		assertEquals(0, ni.nota().totalSaleValue().compareTo(new BigDecimal("102.00")), "4 x S/ 25.50");
		assertEquals(0, ni.items().get(0).saleUnitPrice().compareTo(new BigDecimal("25.50")));

		// El precio queda congelado: cambiarlo despues no altera lo vendido.
		precioVenta("GOYDELSAC", "AC01002", "30.00");

		// A un CECO de TDEM no es venta.
		NotaInventarioDetalleDto propia = despacho.crearNotaInventario(tdem.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(tdem.itemId, new BigDecimal("2"))), 1, null, null),
				logistica);
		assertNull(propia.nota().empresaVentaCode());
		assertNull(propia.items().get(0).saleUnitPrice());

		VentasDto v = ventas.obtener(30, null, gerencia);
		assertEquals(1, v.totales().size(), "solo GOYDEL compra");
		assertEquals("GOYDELSAC", v.totales().get(0).empresa());
		assertEquals(0, v.totales().get(0).monto().compareTo(new BigDecimal("102.00")));
		assertEquals(0, v.totales().get(0).sinPrecio());
		assertEquals("VI-G1", v.porCeco().get(0).clave());
		assertEquals(ni.nota().inventoryNoteCode(), v.lineas().get(0).documento());
		assertTrue(ventas.obtener(30, "METRIN", gerencia).totales().isEmpty());

		byte[] xlsx = dashboard.ventasExcel(30, null, gerencia).getBody();
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet hoja = wb.getSheet("VENTAS");
			assertEquals(1, hoja.getLastRowNum(), "una linea vendida");
			assertEquals(102.0, hoja.getRow(1).getCell(10).getNumericCellValue(), 0.001);
		}

		assertThrows(ApiException.class, () -> ventas.obtener(30, null,
				new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"))),
				"el aprobador de CECO no ve las ventas entre empresas");
	}

	@Test
	void sinPrecioDeVentaSeDespachaPeroQuedaMarcado() {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		Despachable metrin = requerimientoConStock("METRIN", "VI-M1", "AC01003-00", 1);
		NotaInventarioDetalleDto ni = despacho.crearNotaInventario(metrin.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(metrin.itemId, BigDecimal.ONE)), 1, null, null),
				logistica);
		assertEquals("METRIN", ni.nota().empresaVentaCode());
		assertNull(ni.nota().totalSaleValue(), "sin precio no se inventa un valor");
		VentasDto v = ventas.obtener(30, "METRIN", logistica);
		assertEquals(1, v.totales().get(0).sinPrecio());
	}

	// ------------------------------------------------------------------

	private record Despachable(UUID requestId, UUID itemId) {
	}

	/** REQ en atencion de la empresa y CECO dados, con su cantidad ya reservada en el almacen central. */
	private Despachable requerimientoConStock(String empresa, String ceco, String variante, int qty) {
		UUID logisticaId = usuario("logistica@logistica.test");
		UUID empresaId = jdbc.queryForObject("select company_id from empresa where code = ?", UUID.class, empresa);
		jdbc.update("""
				insert into ceco (company_id, code, name) values (?, ?, 'Proyecto ' || ?)
				on conflict (company_id, code) do nothing""", empresaId, ceco, ceco);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = ?",
				UUID.class, empresaId, ceco);
		UUID producto = jdbc.queryForObject("select product_id from producto_variante where internal_variant_code = ?",
				UUID.class, variante);
		UUID varianteId = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?",
				UUID.class, variante);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		UUID almacen = jdbc.queryForObject("select warehouse_id from almacen where is_group_central", UUID.class);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba venta', 'EN_ATENCION') returning request_id""",
				UUID.class, "REQ-VI-" + UUID.randomUUID().toString().substring(0, 6), empresaId, cecoId,
				usuario("solicitante@logistica.test"));
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, ?) returning request_item_id""", UUID.class, req, producto, varianteId, uom, qty);
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				select a.company_id, a.warehouse_id, ?, ?, 'AJUSTE', ?, 'AJUSTE', gen_random_uuid(), ?
				  from almacen a where a.warehouse_id = ?""", producto, varianteId, qty, logisticaId, almacen);
		jdbc.update("""
				insert into reserva_stock (request_item_id, warehouse_id, product_id, variant_id, qty_reserved, status,
				  reserved_at, origin)
				values (?, ?, ?, ?, ?, 'RESERVADA', now(), 'VERIFICACION_STOCK')""",
				item, almacen, producto, varianteId, qty);
		return new Despachable(req, item);
	}

	private void precioVenta(String empresa, String skuBase, String precio) {
		jdbc.update("""
				update precio_venta_producto set active = false
				 where active and company_id = (select company_id from empresa where code = ?)
				   and product_id = (select product_id from producto where sku_base = ?)""", empresa, skuBase);
		jdbc.update("""
				insert into precio_venta_producto (company_id, product_id, sale_price, created_by)
				select e.company_id, p.product_id, ?, ? from empresa e, producto p where e.code = ? and p.sku_base = ?""",
				new BigDecimal(precio), usuario("logistica@logistica.test"), empresa, skuBase);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
