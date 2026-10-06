package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.dashboard.DashboardDto;
import com.tdem.logistica.modules.dashboard.DashboardService;
import com.tdem.logistica.modules.tracking.TrackingService;
import com.tdem.logistica.modules.tracking.dto.EventoDto;
import com.tdem.logistica.modules.tracking.dto.HitoDto;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 6 de punta a punta contra Postgres real (BdRrhhSimulada) con los
 * servicios reales: recepcion parcial/total de una OC, incidencia, despacho
 * en dos Notas de Inventario, envio, confirmacion en destino y calculo
 * automatico de la atencion (RF-079). Cubre tambien la regresion del
 * pendiente de compra al recibir/despachar (DEC-L017).
 *
 * Escenario: REQ de 12 u. -> 2 u. cubiertas con stock existente (Lote 3) y
 * OC por las 10 u. restantes a S/ 5.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class AlmacenFlujoTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private RecepcionService recepcionService;
	@Autowired private DespachoService despachoService;
	@Autowired private ComprasService comprasService;
	@Autowired private TrackingService trackingService;
	@Autowired private DashboardService dashboardService;
	@Autowired private com.tdem.logistica.modules.tracking.TrackingController trackingController;

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
	void recepcionDespachoEnvioYAtencionAutomatica() {
		UUID logisticaId = usuario("logistica@logistica.test");
		UsuarioPrincipal logistica = new UsuarioPrincipal(logisticaId, List.of("LOGISTICA"));
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		Fixture f = crearFixture(logisticaId);

		assertEquals(0, pendienteCompra(f, logistica).signum(), "12 - 2 en stock - 10 ordenadas");

		// --- Recepcion parcial: 6 de 10 ---
		recibir(f, logistica, "6", null);
		assertEquals("RECEPCION_PARCIAL", estadoOrden(f));
		assertEquals(0, kardex(f).compareTo(new BigDecimal("8")), "2 de stock + 6 recibidas");
		assertEquals(0, pendienteCompra(f, logistica).signum(), "recibir no reabre la compra");

		// --- No se puede recibir mas de lo pendiente (4) ---
		ApiException exceso = assertThrows(ApiException.class, () -> recibir(f, logistica, "5", null));
		assertTrue(exceso.getMessage().contains("supera lo pendiente"), exceso.getMessage());

		// --- Incidencia sin recibir nada: el pendiente no cambia ---
		RecepcionOrdenDto conIncidencia = recibir(f, logistica, "0",
				new IncidenciaInput("DANO", new BigDecimal("1"), "Caja aplastada", "REPONER"));
		assertEquals(1, conIncidencia.incidencias().size());
		assertEquals("OBSERVADA", conIncidencia.recepciones().get(1).status());
		assertEquals(0, conIncidencia.items().get(0).qtyPending().compareTo(new BigDecimal("4")));

		// --- Recepcion final: 4 -> OC completa ---
		recibir(f, logistica, "4", null);
		assertEquals("RECEPCION_COMPLETA", estadoOrden(f));
		assertEquals(0, kardex(f).compareTo(new BigDecimal("12")));

		// --- OC con recepciones no se puede anular ---
		assertThrows(ApiException.class, () -> comprasService.anularOrden(f.orderId, "Prueba", logistica));

		// --- Despacho 1: 9 u. (FIFO: 2 de stock + 7 recibidas) ---
		NotaInventarioDetalleDto ni1 = despachoService.crearNotaInventario(f.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(f.requestItemId, new BigDecimal("9"))), 2, null, null),
				logistica);
		assertEquals("NI-1-TDEMSRL", ni1.nota().inventoryNoteCode());
		assertEquals("EMITIDA", ni1.nota().status());
		assertEquals(0, kardex(f).compareTo(new BigDecimal("3")));
		assertEquals(0, pendienteCompra(f, logistica).signum(), "despachar no reabre la compra");

		// --- No se despacha mas de lo reservado (quedan 3) ---
		assertThrows(ApiException.class, () -> despachoService.crearNotaInventario(f.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(f.requestItemId, new BigDecimal("4"))), null, null, null),
				logistica));

		NotaInventarioDetalleDto ni2 = despachoService.crearNotaInventario(f.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(f.requestItemId, new BigDecimal("3"))), 1, "NUEVO", null),
				logistica);
		assertEquals("NI-2-TDEMSRL", ni2.nota().inventoryNoteCode());
		assertEquals(0, kardex(f).signum());
		assertEquals("EN_ATENCION", estadoRequerimiento(f), "despachar no es entregar (RF-079)");

		// --- Envio + confirmacion de la NI 1 -> atencion parcial ---
		NotaInventarioDetalleDto enviada1 = despachoService.registrarEnvio(ni1.nota().inventoryNoteId(),
				new RegistrarEnvioRequest("AGENCIA", "Shalom", "SH-001", null, null, "PEN",
						new BigDecimal("50"), new BigDecimal("9"), LocalDate.now(), LocalDate.now().plusDays(2)),
				logistica);
		assertEquals("DESPACHADA", enviada1.nota().status());
		assertEquals(0, enviada1.nota().envio().shippingCostTotal().compareTo(new BigDecimal("59")));
		UUID envio1 = enviada1.nota().envio().shipmentId();

		assertThrows(ApiException.class, () -> despachoService.confirmarRecepcion(envio1, null, solicitante),
				"solo LOGISTICA/ADMIN confirma (DEC-L017)");

		despachoService.confirmarRecepcion(envio1, new ConfirmarRecepcionRequest("Conforme"), logistica);
		assertEquals("ATENCION_PARCIAL", estadoRequerimiento(f));

		// --- Tracking a mitad de camino (RF-083): 9/12 entregadas ---
		TrackingDto parcial = trackingService.obtener(f.requestId, solicitante);
		assertEquals(75, parcial.porcentajeAtencion());
		assertTrue(hito(parcial, "RECEPCIONADO").cumplido());
		assertTrue(hito(parcial, "ATENCION_PARCIAL").cumplido());
		assertEquals("9/12 u.", hito(parcial, "ATENCION_PARCIAL").detalle());
		assertFalse(hito(parcial, "ATENCION_COMPLETA").cumplido());
		assertEquals(2, parcial.entregas().size());
		assertEquals("RECEPCIONADO", parcial.entregas().get(0).estadoEntrega());
		assertEquals("SIN_ENVIO", parcial.entregas().get(1).estadoEntrega());

		// --- Envio + confirmacion de la NI 2 -> atencion completa ---
		UUID envio2 = despachoService.registrarEnvio(ni2.nota().inventoryNoteId(),
				new RegistrarEnvioRequest("RECOJO", null, null, null, null, null, null, null, null, null),
				logistica).nota().envio().shipmentId();
		NotaInventarioDetalleDto recibida2 = despachoService.confirmarRecepcion(envio2, null, logistica);
		assertEquals("RECEPCIONADA", recibida2.nota().status());
		assertEquals("ATENCION_COMPLETA", estadoRequerimiento(f));

		// --- Confirmar dos veces el mismo envio no esta permitido ---
		assertThrows(ApiException.class, () -> despachoService.confirmarRecepcion(envio2, null, logistica));

		// --- Tracking final (RF-039/081/083) ---
		TrackingDto t = trackingService.obtener(f.requestId, solicitante);
		assertEquals(100, t.porcentajeAtencion());
		assertEquals(0, t.unidadesEntregadas().compareTo(new BigDecimal("12")));
		for (String codigo : List.of("EMITIDO", "APROBADO", "RECEPCIONADO", "ATENCION_PARCIAL", "ATENCION_COMPLETA")) {
			assertTrue(hito(t, codigo).cumplido(), codigo);
		}
		assertEquals(List.of("NI-1-TDEMSRL", "NI-2-TDEMSRL"), t.entregas().stream().map(e -> e.notaCodigo()).toList());
		assertEquals("Shalom", t.entregas().get(0).transportista());
		assertEquals(0, t.entregas().get(0).costoEnvioNeto().compareTo(new BigDecimal("50")));
		assertTrue(t.entregas().get(0).itemsDespachados().contains("AC01001-00"), t.entregas().get(0).itemsDespachados());
		List<String> etapas = t.eventos().stream().map(EventoDto::etapa).toList();
		assertTrue(etapas.containsAll(List.of("Requerimiento", "Stock", "Compras", "Almacén", "Despacho", "Envío", "Entrega")),
				etapas.toString());
		assertEquals(3, t.eventos().stream().filter(e -> e.etapa().equals("Almacén")).count(), "3 recepciones de la OC");

		// Excel: una fila por NI con las 17 columnas del prototipo V38.
		byte[] xlsx = trackingController.excel(f.requestId, logistica).getBody();
		try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
			var hoja = wb.getSheet("TRACKING");
			assertEquals(17, hoja.getRow(0).getLastCellNum());
			assertEquals(2, hoja.getLastRowNum());
			assertEquals("REQ-TEST-ALM", hoja.getRow(1).getCell(0).getStringCellValue());
			assertEquals("NI-2-TDEMSRL", hoja.getRow(2).getCell(8).getStringCellValue());
			assertEquals(50.0, hoja.getRow(1).getCell(13).getNumericCellValue());
		} catch (IOException e) {
			throw new AssertionError(e);
		}

		// --- Dashboard (DEC-L020): el REQ completo y el comparativo 50 vs 70 ---
		DashboardDto d = dashboardService.obtener(30, "TDEMSRL", logistica);
		assertTrue(d.completados() >= 1);
		assertNotNull(d.diasCiclo());
		assertTrue(d.rfqEnviadas() >= 2 && d.rfqRespondidas() >= 2, d.toString());
		assertEquals(5, d.etapas().size());
		DashboardDto.Comparativo comp = d.comparativos().stream()
				.filter(c -> c.codigo().equals("REQ-TEST-ALM")).findFirst().orElseThrow();
		assertEquals(2, comp.cotizaciones());
		assertEquals(0, comp.ahorro().compareTo(new BigDecimal("20")), "70 cotizado - 50 elegido");
		assertTrue(d.ahorro().stream().anyMatch(a -> a.moneda().equals("PEN") && a.monto().signum() > 0));
		assertTrue(dashboardService.obtener(30, "METRIN", logistica).comparativos().stream()
				.noneMatch(c -> c.codigo().equals("REQ-TEST-ALM")), "el filtro de empresa excluye a TDEM");
		assertThrows(ApiException.class, () -> dashboardService.obtener(30, null, solicitante),
				"el solicitante no ve el dashboard");

		// Otro solicitante no ve el Tracking ajeno.
		UsuarioPrincipal ajeno = new UsuarioPrincipal(UUID.randomUUID(), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> trackingService.obtener(f.requestId, ajeno));
	}

	// ------------------------------------------------------------------

	private static HitoDto hito(TrackingDto t, String codigo) {
		return t.hitos().stream().filter(h -> h.codigo().equals(codigo)).findFirst().orElseThrow();
	}

	private record Fixture(UUID requestId, UUID requestItemId, UUID orderId, UUID orderItemId,
						   UUID productId, UUID variantId) {
	}

	private RecepcionOrdenDto recibir(Fixture f, UsuarioPrincipal p, String qty, IncidenciaInput incidencia) {
		return recepcionService.registrar(f.orderId, new RegistrarRecepcionRequest(
				List.of(new RecepcionItemInput(f.orderItemId, new BigDecimal(qty), "CONFORME", null, incidencia)),
				null, null), p);
	}

	private BigDecimal pendienteCompra(Fixture f, UsuarioPrincipal p) {
		return comprasService.obtenerDetalleCompra(f.requestId, p).items().stream()
				.filter(i -> i.requestItemId().equals(f.requestItemId))
				.findFirst().orElseThrow().cantidadPendiente();
	}

	private BigDecimal kardex(Fixture f) {
		return jdbc.queryForObject("select coalesce(sum(qty), 0) from movimiento_inventario "
				+ "where product_id = ? and variant_id = ?", BigDecimal.class, f.productId, f.variantId);
	}

	private String estadoOrden(Fixture f) {
		return jdbc.queryForObject("select status from orden_abastecimiento where order_id = ?", String.class, f.orderId);
	}

	private String estadoRequerimiento(Fixture f) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, f.requestId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}

	/** REQ de 12 u. en atencion, 2 u. reservadas de stock existente y OC por 10 u. */
	private Fixture crearFixture(UUID logisticaId) {
		UUID solicitanteId = usuario("solicitante@logistica.test");
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = 'AC01001'", UUID.class);
		UUID variante = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		UUID almacen = jdbc.queryForObject("select warehouse_id from almacen where company_id = ? and code = 'CENTRAL'", UUID.class, empresa);

		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values ('REQ-TEST-ALM', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba almacen', 'EN_ATENCION')
				returning request_id""", UUID.class, empresa, ceco, solicitanteId);
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, 12) returning request_item_id""", UUID.class, req, producto, variante, uom);

		// 2 u. de stock existente, reservadas en la verificacion (Lote 3), antes que lo comprado (FIFO).
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				values (?, ?, ?, ?, 'AJUSTE', 2, 'AJUSTE', gen_random_uuid(), ?)""", empresa, almacen, producto, variante, logisticaId);
		jdbc.update("""
				insert into reserva_stock (request_item_id, warehouse_id, product_id, variant_id, qty_reserved, status,
				  reserved_at, origin)
				values (?, ?, ?, ?, 2, 'RESERVADA', now() - interval '1 day', 'VERIFICACION_STOCK')""",
				item, almacen, producto, variante);

		UUID proveedor = jdbc.queryForObject("""
				insert into proveedor (ruc, legal_name) values ('20123456789', 'Proveedor de Prueba SAC')
				returning supplier_id""", UUID.class);
		UUID rfq = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-TEST-ALM', ?, ?, ?, 'CERRADA', ?) returning rfq_id""", UUID.class, empresa, req, proveedor, logisticaId);
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
				values ('OC-TEST-ALM', ?, ?, ?, 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', 50, 50, 'EMITIDA', ?)
				returning order_id""", UUID.class, empresa, proveedor, cotizacion, logisticaId);
		// Segunda oferta, mas cara y no elegida (S/ 70): el dashboard mide el ahorro del comparativo.
		UUID proveedor2 = jdbc.queryForObject("""
				insert into proveedor (ruc, legal_name) values ('20987654321', 'Otro Proveedor SAC')
				returning supplier_id""", UUID.class);
		UUID rfq2 = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-TEST-ALM-2', ?, ?, ?, 'CERRADA', ?) returning rfq_id""", UUID.class, empresa, req, proveedor2, logisticaId);
		jdbc.update("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
				values (?, ?, 'PEN', 70, 70, 'RECIBIDA')""", rfq2, proveedor2);

		UUID ordenItem = jdbc.queryForObject("""
				insert into orden_item (order_id, request_item_id, product_id, variant_id, description, uom_id,
				  qty_ordered, unit_cost, line_subtotal, line_total)
				values (?, ?, ?, ?, 'Producto de prueba', ?, 10, 5, 50, 50) returning order_item_id""",
				UUID.class, orden, item, producto, variante, uom);

		return new Fixture(req, item, orden, ordenItem, producto, variante);
	}
}
