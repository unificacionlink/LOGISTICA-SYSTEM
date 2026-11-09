package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.cajachica.dto.EvaluacionCajaChicaDto;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.dto.*;
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
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 16 (DEC-L039): al aprobar, el faltante crea una necesidad que entra al
 * consolidado de su SKU (entre requerimientos y CECOs); Logistica prepara RFQ
 * multiproducto por proveedor, adjudica por producto y emite la OC por
 * cotizacion; lo recibido se reparte entre los requerimientos de origen.
 * AprobacionService.decidir real sobre Postgres (BdRrhhSimulada). Cada test usa
 * SKUs distintos.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ConsolidacionComprasTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComprasService compras;
	@Autowired private RecepcionService recepcion;
	@Autowired private CajaChicaService cajaChica;

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
	void stockParcialCreaNecesidadQueEntraAlConsolidado() {
		stock("MU03004-00", 2);
		Req req = requerimiento("ADM", "NORMAL", null, Map.of("MU03004-00", 5));

		aprobar(req);

		UUID item = req.items.get("MU03004-00");
		assertEquals("EN_ATENCION", estado(req));
		assertEquals(0, reservado(item).compareTo(new BigDecimal("2")));
		Map<String, Object> n = jdbc.queryForMap("""
				select n.status, n.qty, c.status as consolidado, c.total_qty from necesidad_compra n
				join consolidacion c on c.consolidation_id = n.consolidation_id where n.request_item_id = ?""", item);
		assertEquals("COMPRA", n.get("status"));
		assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) n.get("qty")));
		assertEquals("ABIERTA", n.get("consolidado"));

		ConsolidadoDto c = consolidado("MU03004-00");
		assertTrue(c.proveedores().isEmpty());
		ApiException sin = assertThrows(ApiException.class,
				() -> abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica()));
		assertTrue(sin.getMessage().contains("Sin proveedor habilitado: MU03004"), sin.getMessage());
	}

	@Test
	void conStockSuficienteNoHayNecesidad() {
		stock("MU03006-00", 10);
		Req req = requerimiento("ADM", "NORMAL", null, Map.of("MU03006-00", 4));

		aprobar(req);

		assertEquals(0, reservado(req.items.get("MU03006-00")).compareTo(new BigDecimal("4")));
		assertEquals(0, jdbc.queryForObject("select count(*) from necesidad_compra where request_item_id = ?",
				Integer.class, req.items.get("MU03006-00")));
	}

	@Test
	void consolidaEntreRequerimientosCotizaPorProveedorAdjudicaYReparte() {
		UsuarioPrincipal logistica = logistica();
		UUID s1 = proveedor("20100000011");
		UUID s2 = proveedor("20100000012");
		abastecimiento.reemplazarCobertura(producto("AC01001"), List.of(s1, s2), logistica);
		abastecimiento.reemplazarCobertura(producto("AC01002"), List.of(s1), logistica);

		Map<String, Integer> pedidoA = new LinkedHashMap<>();
		pedidoA.put("AC01001-00", 3);
		pedidoA.put("AC01002-00", 2);
		Req a = requerimiento("ADM", "NORMAL", null, pedidoA);
		Req b = requerimiento("OPS", "URGENTE", LocalDate.now(LIMA).plusDays(10), Map.of("AC01001-00", 4));
		aprobar(a);
		aprobar(b);

		// --- Un consolidado por SKU, con el REQ y el CECO de cada parte ---
		ConsolidadoDto cx = consolidado("AC01001-00");
		ConsolidadoDto cy = consolidado("AC01002-00");
		assertEquals(0, cx.totalQty().compareTo(new BigDecimal("7")));
		assertEquals(List.of("ADM", "OPS"), cx.origenes().stream().map(OrigenDto::cecoCode).sorted().toList());
		assertEquals(2, cx.proveedores().size());

		// --- RFQ multiproducto: una por proveedor ---
		List<RfqDto> rfqs = abastecimiento.prepararSolicitudes(List.of(cx.consolidationId(), cy.consolidationId()), logistica);
		assertEquals(2, rfqs.size());
		RfqDto r1 = rfqs.stream().filter(r -> r.supplierId().equals(s1)).findFirst().orElseThrow();
		RfqDto r2 = rfqs.stream().filter(r -> r.supplierId().equals(s2)).findFirst().orElseThrow();
		assertEquals("BORRADOR", r1.status());
		assertNull(r1.requestId(), "cotiza dos requerimientos");
		assertEquals(2, compras.obtenerRfq(r1.rfqId(), logistica).items().size());
		assertEquals(1, compras.obtenerRfq(r2.rfqId(), logistica).items().size());
		assertThrows(ApiException.class, () -> abastecimiento.prepararSolicitudes(List.of(cx.consolidationId()), logistica),
				"un consolidado se cotiza una sola vez");

		// --- Lo aprobado despues abre otro consolidado ---
		Req c = requerimiento("ADM", "NORMAL", null, Map.of("AC01001-00", 1));
		aprobar(c);
		ConsolidadoDto cx2 = consolidado("AC01001-00");
		assertNotEquals(cx.consolidationId(), cx2.consolidationId());
		assertEquals(0, cx2.totalQty().compareTo(BigDecimal.ONE));

		// --- Envio ---
		assertEquals("ENVIADA", abastecimiento.confirmarEnvio(r1.rfqId(), "CORREO", logistica).status());
		assertThrows(ApiException.class, () -> abastecimiento.confirmarEnvio(r1.rfqId(), "CORREO", logistica));

		// --- Cotizaciones: S1 cotiza ambos, S2 solo AC01001 y mas barato ---
		CotizacionDto cot1 = cotizar(r1.rfqId(), Map.of("AC01001", "10", "AC01002", "5"));
		CotizacionDto cot2 = cotizar(r2.rfqId(), Map.of("AC01001", "8"));
		assertEquals("RESPONDIDA", compras.obtenerRfq(r2.rfqId(), logistica).rfq().status());

		ConsolidadoDetalleDto det = abastecimiento.obtenerConsolidado(cx.consolidationId(), logistica);
		assertEquals(2, det.ofertas().size());
		assertEquals(0, det.ofertas().get(0).unitCost().compareTo(new BigDecimal("8")), "la mas barata primero");
		assertEquals(2, det.consolidado().cotizaciones());
		assertEquals(0, det.consolidado().cantidadCotizada().compareTo(new BigDecimal("7")),
				"la mayor cantidad ofertada, no la suma de las dos ofertas");
		assertNull(cx2.cantidadCotizada(), "sin ofertas todavia");

		// --- Adjudicacion por producto a proveedores distintos ---
		abastecimiento.adjudicar(cx.consolidationId(), linea(cot2, "AC01001"), logistica);
		ApiException ajena = assertThrows(ApiException.class,
				() -> abastecimiento.adjudicar(cx.consolidationId(), linea(cot1, "AC01002"), logistica));
		assertTrue(ajena.getMessage().contains("no corresponde"), ajena.getMessage());
		abastecimiento.adjudicar(cy.consolidationId(), linea(cot1, "AC01002"), logistica);
		assertThrows(ApiException.class, () -> compras.descartarCotizacion(cot1.quotationId(), logistica),
				"tiene un producto adjudicado");

		List<PorEmitirDto> porEmitir = abastecimiento.listarPorEmitir(logistica);
		assertEquals(2, porEmitir.size());

		// --- OC por cotizacion ---
		OrdenDto oc2 = abastecimiento.emitirOrden(cot2.quotationId(), logistica);
		assertEquals(0, oc2.total().compareTo(new BigDecimal("56.00")));
		assertNull(oc2.requestId());
		assertTrue(oc2.requestCode().contains(a.codigo) && oc2.requestCode().contains(b.codigo), oc2.requestCode());
		assertThrows(ApiException.class, () -> abastecimiento.emitirOrden(cot2.quotationId(), logistica));
		OrdenDto oc1 = abastecimiento.emitirOrden(cot1.quotationId(), logistica);
		assertEquals(0, oc1.total().compareTo(new BigDecimal("10.00")), "solo lo adjudicado a S1");
		assertEquals("CERRADA", estadoRfq(r1.rfqId()));
		assertEquals("CERRADA", estadoRfq(r2.rfqId()));
		assertEquals(1, jdbc.queryForObject("select count(*) from proveedor_producto where supplier_id = ? and product_id = ?",
				Integer.class, s2, producto("AC01001")));

		// --- Recepcion: primero el urgente (B: 4), luego A ---
		UUID linea = jdbc.queryForObject("select order_item_id from orden_item where order_id = ?", UUID.class, oc2.orderId());
		recibir(oc2.orderId(), linea, "5");
		assertEquals(0, reservadoPorRecepcion(b.items.get("AC01001-00")).compareTo(new BigDecimal("4")));
		assertEquals(0, reservadoPorRecepcion(a.items.get("AC01001-00")).compareTo(BigDecimal.ONE));
		recibir(oc2.orderId(), linea, "2");
		assertEquals(0, reservadoPorRecepcion(a.items.get("AC01001-00")).compareTo(new BigDecimal("3")));
		assertEquals(0, reservadoPorRecepcion(b.items.get("AC01001-00")).compareTo(new BigDecimal("4")));

		// --- El detalle de compras del REQ ve sus RFQ y necesidades ---
		ComprasDetalleDto detA = compras.obtenerDetalleCompra(a.id, logistica);
		assertEquals(2, detA.rfqs().size());
		assertEquals(2, detA.necesidades().size());
	}

	@Test
	void mismoDiaEsperaLaRutaYPuedeVolverACajaChica() {
		UsuarioPrincipal logistica = logistica();
		Req req = requerimiento("ADM", "URGENTE", LocalDate.now(LIMA), Map.of("MU03005-00", 4));
		aprobar(req);

		NecesidadDto n = abastecimiento.listarNecesidades(logistica).stream()
				.filter(x -> x.requestId().equals(req.id)).findFirst().orElseThrow();
		assertEquals("PENDIENTE_RUTA", n.status());
		assertEquals("MISMO_DIA", n.suggestedReason());
		assertEquals(0, jdbc.queryForObject("select count(*) from consolidacion c join producto p on p.product_id = c.product_id "
				+ "where p.sku_base = 'MU03005'", Integer.class));

		NecesidadDto compra = abastecimiento.decidirRuta(n.needId(), "COMPRA", logistica);
		assertEquals("COMPRA", compra.status());
		assertNotNull(compra.consolidationCode());
		assertThrows(ApiException.class, () -> abastecimiento.decidirRuta(n.needId(), "OTRA", logistica));

		NecesidadDto caja = abastecimiento.decidirRuta(n.needId(), "CAJA_CHICA", logistica);
		assertEquals("CAJA_CHICA", caja.status());
		assertEquals(0, jdbc.queryForObject("select count(*) from consolidacion c join producto p on p.product_id = c.product_id "
				+ "where p.sku_base = 'MU03005'", Integer.class), "el consolidado vacio se elimina");
		assertEquals(0, cajaChica.pendientesCajaChica(req.id).get(req.items.get("MU03005-00")).compareTo(new BigDecimal("4")));
	}

	@Test
	void anularElConsolidadoDevuelveLosFaltantesARuta() {
		UsuarioPrincipal logistica = logistica();
		UUID s = proveedor("20100000013");
		abastecimiento.reemplazarCobertura(producto("AC01003"), List.of(s), logistica);
		Req req = requerimiento("ADM", "NORMAL", null, Map.of("AC01003-00", 2));
		aprobar(req);
		ConsolidadoDto c = consolidado("AC01003-00");
		RfqDto rfq = abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica).get(0);

		assertThrows(ApiException.class, () -> abastecimiento.anularConsolidado(c.consolidationId(), " ", logistica));
		ConsolidadoDetalleDto anulado = abastecimiento.anularConsolidado(c.consolidationId(), "Proveedor sin stock", logistica);

		assertEquals("ANULADA", anulado.consolidado().status());
		assertEquals("CERRADA", estadoRfq(rfq.rfqId()));
		Map<String, Object> nueva = jdbc.queryForMap("""
				select status, suggested_reason, qty from necesidad_compra where request_item_id = ? and status <> 'ANULADA'""",
				req.items.get("AC01003-00"));
		assertEquals("PENDIENTE_RUTA", nueva.get("status"));
		assertEquals("FALLA_PROVEEDOR", nueva.get("suggested_reason"));
		EvaluacionCajaChicaDto ev = cajaChica.evaluar(req.id, logistica);
		assertTrue(ev.motivos().stream().anyMatch(m -> m.codigo().equals("FALLA_PROVEEDOR")), ev.toString());
	}

	@Test
	void soloLogisticaGestionaCompras() {
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> abastecimiento.listarNecesidades(solicitante));
		assertThrows(ApiException.class, () -> abastecimiento.listarConsolidados(solicitante));
		assertThrows(ApiException.class, () -> abastecimiento.reemplazarCobertura(producto("AC01004"), List.of(), solicitante));
	}

	// ------------------------------------------------------------------

	private record Req(UUID id, String codigo, UUID approvalId, Map<String, UUID> items) {
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private void aprobar(Req req) {
		UsuarioPrincipal aprobador = new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"));
		aprobacionService.decidir(req.approvalId, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
	}

	private ConsolidadoDto consolidado(String codigoVariante) {
		return abastecimiento.listarConsolidados(logistica()).stream()
				.filter(c -> codigoVariante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow();
	}

	private CotizacionDto cotizar(UUID rfqId, Map<String, String> costos) {
		List<CotizacionItemInput> items = compras.obtenerRfq(rfqId, logistica()).items().stream()
				.filter(i -> costos.containsKey(i.skuBase()))
				.map(i -> new CotizacionItemInput(i.rfqItemId(), i.qty(), new BigDecimal(costos.get(i.skuBase())), null, null))
				.toList();
		return compras.registrarCotizacion(rfqId, new RegistrarCotizacionRequest(null, null, null, "PEN", "CREDITO_30",
				"ALMACEN_CLIENTE", 5, null, items), logistica());
	}

	private static UUID linea(CotizacionDto cot, String sku) {
		return cot.items().stream().filter(i -> i.skuBase().equals(sku)).findFirst().orElseThrow().quotationItemId();
	}

	private void recibir(UUID orderId, UUID orderItemId, String qty) {
		recepcion.registrar(orderId, new RegistrarRecepcionRequest(
				List.of(new RecepcionItemInput(orderItemId, new BigDecimal(qty), "CONFORME", null, null)), null, null),
				logistica());
	}

	private Req requerimiento(String ceco, String prioridad, LocalDate requerida, Map<String, Integer> pedido) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = ?", UUID.class, empresa, ceco);
		String codigo = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority, required_date)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', ?, ?) returning request_id""",
				UUID.class, codigo, empresa, cecoId, usuario("solicitante@logistica.test"), prioridad, requerida);
		Map<String, UUID> items = new LinkedHashMap<>();
		pedido.forEach((variante, qty) -> items.put(variante, jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ? returning request_item_id""", UUID.class, reqId, qty, variante)));
		UUID approvalId = jdbc.queryForObject("""
				insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id""",
				UUID.class, reqId);
		return new Req(reqId, codigo, approvalId, items);
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

	private UUID producto(String skuBase) {
		return jdbc.queryForObject("select product_id from producto where sku_base = ?", UUID.class, skuBase);
	}

	private String estado(Req req) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, req.id);
	}

	private String estadoRfq(UUID rfqId) {
		return jdbc.queryForObject("select status from rfq where rfq_id = ?", String.class, rfqId);
	}

	private BigDecimal reservado(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock "
				+ "where request_item_id = ? and status = 'RESERVADA'", BigDecimal.class, requestItemId);
	}

	private BigDecimal reservadoPorRecepcion(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock "
				+ "where request_item_id = ? and origin = 'RECEPCION_OC' and need_id is not null", BigDecimal.class, requestItemId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
