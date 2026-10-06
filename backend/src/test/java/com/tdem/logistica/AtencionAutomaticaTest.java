package com.tdem.logistica;

import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.ComprasService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * DEC-L018: al aprobar un requerimiento el sistema aparta el stock disponible
 * y crea sola la RFQ del faltante al proveedor habitual de cada producto, sin
 * el paso manual de verificacion de stock. Usa AprobacionService.decidir real
 * sobre Postgres (BdRrhhSimulada). Cada test usa SKUs distintos.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class AtencionAutomaticaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private ComprasService comprasService;

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
	void stockParcialYProveedorHabitualCreaRfqDelFaltante() {
		stock("MU03004-00", 2);
		UUID proveedor = proveedor("20100000001");
		habitual("MU03004", proveedor);
		Req req = requerimiento(Map.of("MU03004-00", 5));

		aprobar(req);

		UUID item = req.items.get("MU03004-00");
		assertEquals("EN_ATENCION", estado(req));
		assertEquals(0, reservado(item).compareTo(new BigDecimal("2")));
		List<Map<String, Object>> rfqs = rfqs(req);
		assertEquals(1, rfqs.size());
		assertEquals(proveedor, rfqs.get(0).get("supplier_id"));
		assertEquals("ENVIADA", rfqs.get(0).get("status"));
		assertEquals(0, qtyEnRfq(item).compareTo(new BigDecimal("3")));
		assertEquals(0, pendienteCompra(req, item).signum(), "todo el faltante ya esta en RFQ");
		assertFalse(enPendientesDeCompra(req), "con RFQ al habitual no queda en Pendientes de compra");
	}

	@Test
	void sinProveedorHabitualElFaltanteQuedaPendienteDeCompra() {
		stock("MU03005-00", 1);
		Req req = requerimiento(Map.of("MU03005-00", 4));

		aprobar(req);

		UUID item = req.items.get("MU03005-00");
		assertEquals(0, reservado(item).compareTo(BigDecimal.ONE));
		assertEquals(0, rfqs(req).size());
		assertEquals(0, pendienteCompra(req, item).compareTo(new BigDecimal("3")));
		assertTrue(enPendientesDeCompra(req), "sin habitual el faltante va a Pendientes de compra");
	}

	@Test
	void conStockSuficienteSoloReservaYNoCotiza() {
		stock("MU03006-00", 10);
		habitual("MU03006", proveedor("20100000003"));
		Req req = requerimiento(Map.of("MU03006-00", 4));

		aprobar(req);

		assertEquals(0, reservado(req.items.get("MU03006-00")).compareTo(new BigDecimal("4")));
		assertEquals(0, rfqs(req).size());
		assertEquals("EN_ATENCION", estado(req));
		assertFalse(enPendientesDeCompra(req), "con stock suficiente no hay nada que comprar");
	}

	@Test
	void productosConProveedoresDistintosGeneranUnaRfqPorProveedor() {
		UUID p1 = proveedor("20100000004");
		UUID p2 = proveedor("20100000005");
		habitual("AC01001", p1);
		habitual("AC01002", p2);
		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01001-00", 3);
		pedido.put("AC01002-00", 7);
		Req req = requerimiento(pedido);

		aprobar(req);

		List<Map<String, Object>> rfqs = rfqs(req);
		assertEquals(2, rfqs.size());
		assertEquals(0, qtyEnRfq(req.items.get("AC01001-00")).compareTo(new BigDecimal("3")));
		assertEquals(0, qtyEnRfq(req.items.get("AC01002-00")).compareTo(new BigDecimal("7")));
	}

	@Test
	void emitirLaOcFijaElProveedorHabitualSiNoTenia() {
		UUID proveedor = proveedor("20100000006");
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = 'AC01003'", UUID.class);
		assertNull(jdbc.queryForObject("select preferred_supplier_id from producto where product_id = ?", UUID.class, producto));

		Req req = requerimiento(Map.of("AC01003-00", 2));
		aprobar(req);  // sin stock ni proveedor habitual: queda para RFQ manual
		UUID item = req.items.get("AC01003-00");
		UUID variante = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = 'AC01003-00'", UUID.class);
		UUID logisticaId = usuario("logistica@logistica.test");
		UUID rfq = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-MANUAL-AC01003', (select company_id from empresa where code = 'TDEMSRL'), ?, ?, 'ENVIADA', ?)
				returning rfq_id""", UUID.class, req.id, proveedor, logisticaId);
		UUID rfqItem = jdbc.queryForObject("""
				insert into rfq_item (rfq_id, request_item_id, product_id, variant_id, uom_id, qty)
				values (?, ?, ?, ?, (select base_uom_id from producto where product_id = ?), 2) returning rfq_item_id""",
				UUID.class, rfq, item, producto, variante, producto);
		UUID cotizacion = jdbc.queryForObject("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, payment_condition, delivery_mode)
				values (?, ?, 'PEN', 20, 20, 'CONTADO', 'ALMACEN_CLIENTE') returning quotation_id""", UUID.class, rfq, proveedor);
		jdbc.update("""
				insert into cotizacion_item (quotation_id, rfq_item_id, product_id, variant_id, qty, unit_cost, line_total)
				values (?, ?, ?, ?, 2, 10, 20)""", cotizacion, rfqItem, producto, variante);

		comprasService.seleccionarCotizacion(cotizacion, new UsuarioPrincipal(logisticaId, List.of("LOGISTICA")));

		assertEquals(proveedor, jdbc.queryForObject("select preferred_supplier_id from producto where product_id = ?",
				UUID.class, producto));
	}

	// ------------------------------------------------------------------

	private record Req(UUID id, UUID approvalId, Map<String, UUID> items) {
	}

	private void aprobar(Req req) {
		UsuarioPrincipal aprobador = new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"));
		aprobacionService.decidir(req.approvalId, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
	}

	private Req requerimiento(Map<String, Integer> pedido) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION') returning request_id""",
				UUID.class, "REQ-" + UUID.randomUUID().toString().substring(0, 8), empresa, ceco,
				usuario("solicitante@logistica.test"));
		Map<String, UUID> items = new LinkedHashMap<>();
		pedido.forEach((codigo, qty) -> items.put(codigo, jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ? returning request_item_id""", UUID.class, reqId, qty, codigo)));
		UUID approvalId = jdbc.queryForObject("""
				insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id""",
				UUID.class, reqId);
		return new Req(reqId, approvalId, items);
	}

	private void stock(String codigoVariante, int qty) {
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				select a.company_id, a.warehouse_id, v.product_id, v.variant_id, 'AJUSTE', ?, 'AJUSTE', gen_random_uuid(), ?
				from almacen a join empresa e on e.company_id = a.company_id and e.code = 'TDEMSRL'
				cross join producto_variante v where a.code = 'CENTRAL' and v.internal_variant_code = ?""",
				qty, usuario("logistica@logistica.test"), codigoVariante);
	}

	private UUID proveedor(String ruc) {
		return jdbc.queryForObject("insert into proveedor (ruc, legal_name) values (?, ?) returning supplier_id",
				UUID.class, ruc, "Proveedor " + ruc);
	}

	private void habitual(String skuBase, UUID proveedor) {
		jdbc.update("update producto set preferred_supplier_id = ? where sku_base = ?", proveedor, skuBase);
	}

	private String estado(Req req) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, req.id);
	}

	private BigDecimal reservado(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock "
				+ "where request_item_id = ? and status = 'RESERVADA'", BigDecimal.class, requestItemId);
	}

	private BigDecimal qtyEnRfq(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty), 0) from rfq_item where request_item_id = ?",
				BigDecimal.class, requestItemId);
	}

	private List<Map<String, Object>> rfqs(Req req) {
		return jdbc.queryForList("select supplier_id, status from rfq where request_id = ?", req.id);
	}

	private BigDecimal pendienteCompra(Req req, UUID requestItemId) {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		return comprasService.obtenerDetalleCompra(req.id, logistica).items().stream()
				.filter(i -> i.requestItemId().equals(requestItemId))
				.findFirst().orElseThrow().cantidadPendiente();
	}

	/** Consulta agregada de ComprasService.listarPendientes (una sola query, sin N+1). */
	private boolean enPendientesDeCompra(Req req) {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		return comprasService.listarPendientes(logistica).stream().anyMatch(r -> r.id().equals(req.id));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
