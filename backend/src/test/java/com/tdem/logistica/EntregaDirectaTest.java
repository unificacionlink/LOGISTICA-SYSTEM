package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.EntregaDirectaService;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.EntregaDirectaDto;
import com.tdem.logistica.modules.almacen.dto.GuiaInput;
import com.tdem.logistica.modules.almacen.dto.IncidenciaInput;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RegistrarEntregaDirectaRequest;
import com.tdem.logistica.modules.almacen.dto.RegistrarEntregaDirectaRequest.Linea;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.modules.compras.OrdenCompraService;
import com.tdem.logistica.modules.compras.OrdenDirectaService;
import com.tdem.logistica.modules.compras.dto.CondicionesOrdenRequest;
import com.tdem.logistica.modules.compras.dto.CrearOrdenDirectaRequest;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.modules.tracking.TrackingService;
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
 * ALM-04 de la V38.7 (DEC-L084): el proveedor entrega directo en los CECO.
 * Escenario: un consolidado de 10 u. de dos requerimientos (ADM 6 u. del
 * solicitante, OPS 4 u. de otro usuario) en una OC marcada como entrega
 * directa. Cada CECO registra lo suyo con guia y constancia; el kardex registra entrada y salida (DEC-L093).
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class EntregaDirectaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntregaDirectaService entregas;
	@Autowired private OrdenCompraService ordenCompra;
	@Autowired private OrdenDirectaService directas;
	@Autowired private RecepcionService recepcion;
	@Autowired private TrackingService tracking;

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
	void cadaCecoRecibeLoSuyoSinPasarPorElAlmacen() {
		UsuarioPrincipal log = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UUID solicitanteId = usuario("solicitante@logistica.test");
		UsuarioPrincipal solicitante = new UsuarioPrincipal(solicitanteId, List.of("SOLICITANTE"));
		UUID adm = ceco("ADM");
		UUID ops = ceco("OPS");
		UUID reqA = requerimiento("REQ-DIR-A", adm, solicitanteId);
		UUID reqB = requerimiento("REQ-DIR-B", ops, usuario("aprobador@logistica.test"));
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = 'AC01001'", UUID.class);
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		UUID itemA = item(reqA, producto, variante, 6);
		UUID itemB = item(reqB, producto, variante, 4);
		UUID consolidado = consolidado(producto, variante, 10);
		UUID needA = necesidad(itemA, producto, variante, 6, consolidado);
		UUID needB = necesidad(itemB, producto, variante, 4, consolidado);
		UUID orderId = orden(consolidado, producto, variante, 12, log.userId());
		UUID linea = jdbc.queryForObject("select order_item_id from orden_item where order_id = ?", UUID.class, orderId);

		// 12 u. ordenadas para 10 pedidas: las 2 de mas no tienen CECO, no puede ser entrega directa.
		ApiException sobra = assertThrows(ApiException.class, () -> marcar(orderId, true, log));
		assertTrue(sobra.getMessage().contains("almacén"), sobra.getMessage());
		jdbc.update("update orden_item set qty_ordered = 10, line_subtotal = 50, line_total = 50 where order_item_id = ?", linea);
		OrdenDto marcada = marcar(orderId, true, log);
		assertTrue(marcada.entregaDirecta());

		// Aprobada y liberada (DEC-L061); el almacen ya no la recibe.
		jdbc.update("update orden_abastecimiento set status = 'EMITIDA', approved_at = now(), released_at = now() "
				+ "where order_id = ?", orderId);
		ApiException almacen = assertThrows(ApiException.class, () -> recepcion.registrar(orderId,
				new RegistrarRecepcionRequest(List.of(new RecepcionItemInput(linea, BigDecimal.ONE, "CONFORME", null, null)),
						null, null), log));
		assertTrue(almacen.getMessage().contains("entrega directa"), almacen.getMessage());

		EntregaDirectaDto e = entregas.obtener(orderId, log);
		assertEquals(2, e.destinos().size(), "una entrega por CECO");
		EntregaDirectaDto.Linea lineaA = linea(e, adm);
		assertEquals(0, lineaA.asignado().compareTo(new BigDecimal("6")));
		assertEquals(0, linea(e, ops).asignado().compareTo(new BigDecimal("4")));
		assertThrows(ApiException.class, () -> entregas.obtener(orderId, solicitante), "la OC completa es de Logistica");

		// Validaciones: guia obligatoria, constancia, CECO correcto, lo propio y el pendiente.
		assertThrows(ApiException.class, () -> registrar(orderId, adm, null, linea, needA, itemA, "1", null, solicitante));
		assertThrows(ApiException.class, () -> entregas.registrar(orderId, new RegistrarEntregaDirectaRequest(adm,
				guia("G-0"), null, null, List.of(new Linea(linea, needA, itemA, BigDecimal.ONE, null))), null, solicitante),
				"sin constancia");
		assertThrows(ApiException.class, () -> registrar(orderId, ops, guia("G-0"), linea, needA, itemA, "1", null, log),
				"la linea de ADM no va en la recepcion de OPS");
		assertThrows(ApiException.class, () -> registrar(orderId, ops, guia("G-0"), linea, needB, itemB, "1", null,
				solicitante), "el solicitante no recibe lo de otro");
		assertThrows(ApiException.class, () -> registrar(orderId, adm, guia("G-0"), linea, needA, itemA, "7", null,
				solicitante), "supera lo pendiente");

		// El solicitante recibe 4 de sus 6 en obra.
		registrar(orderId, adm, guia("G-1"), linea, needA, itemA, "4", null, solicitante);
		assertEquals("ATENCION_PARCIAL", estado(reqA));
		assertEquals("RECEPCION_PARCIAL", estadoOrden(orderId));
		// DEC-L093: entra al stock y sale al CECO: ENTRADA y SALIDA en el kardex, saldo neto cero.
		assertEquals(2, jdbc.queryForObject("select count(*) from movimiento_inventario where product_id = ?",
				Integer.class, producto), "entrada y salida en el kardex");
		assertEquals(0, jdbc.queryForObject("select coalesce(sum(qty), 0) from movimiento_inventario where product_id = ?",
				BigDecimal.class, producto).signum(), "el saldo no cambia");
		assertThrows(ApiException.class, () -> registrar(orderId, adm, guia("G-1"), linea, needA, itemA, "1", null, log),
				"la guia no se repite");

		// Logistica recibe en OPS 3 de 4; falta 1 (incidencia: el proveedor repone).
		EntregaDirectaDto conFalta = registrar(orderId, ops, guia("G-2"), linea, needB, itemB, "3",
				new IncidenciaInput("FALTANTE", BigDecimal.ONE, "Llegó una caja menos", "REPONER"), log);
		assertEquals(1, conFalta.incidencias().size());
		assertEquals("OBSERVADA", conFalta.recepciones().get(1).status());
		assertEquals(0, linea(conFalta, ops).pendiente().compareTo(BigDecimal.ONE));

		// Lo que el REQ ve: solo sus lineas y su recepcion.
		List<EntregaDirectaDto> deA = entregas.delRequerimiento(reqA, solicitante);
		assertEquals(1, deA.size());
		assertEquals(1, deA.get(0).destinos().size());
		assertEquals(1, deA.get(0).recepciones().size());
		assertTrue(deA.get(0).destinos().get(0).lineas().get(0).puedeRegistrar());
		assertEquals("ADM · " + nombreCeco(adm), deA.get(0).recepciones().get(0).cecoDestino());

		// Se completa: ADM 2 y OPS la reposicion.
		registrar(orderId, adm, guia("G-3"), linea, needA, itemA, "2", null, log);
		registrar(orderId, ops, guia("G-4"), linea, needB, itemB, "1", null, log);
		assertEquals("ATENCION_COMPLETA", estado(reqA));
		assertEquals("ATENCION_COMPLETA", estado(reqB));
		assertEquals("RECEPCION_COMPLETA", estadoOrden(orderId));
		assertThrows(ApiException.class, () -> registrar(orderId, adm, guia("G-5"), linea, needA, itemA, "1", null, log));

		// Tracking: dos entregas directas, atencion completa.
		TrackingDto t = tracking.obtener(reqA, solicitante);
		assertEquals(2, t.entregas().size());
		assertEquals(orderId, t.entregas().get(0).orderId());
		assertEquals(100, t.porcentajeAtencion());
		assertTrue(t.hitos().stream().anyMatch(h -> h.codigo().equals("ATENCION_COMPLETA") && h.cumplido()));
	}

	@Test
	void ordenDirectaConEntregaDirectaExigeCeco() {
		UsuarioPrincipal log = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UUID prov = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20100000091', 'Prov 91') "
				+ "returning supplier_id", UUID.class);
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01006-00'", UUID.class);
		List<CrearOrdenDirectaRequest.Linea> lineas = List.of(
				new CrearOrdenDirectaRequest.Linea(variante, null, new BigDecimal("2"), BigDecimal.TEN));
		assertThrows(ApiException.class, () -> directas.crear(new CrearOrdenDirectaRequest(prov, "PEN",
				"CONTADO", "ALMACEN_CLIENTE", null, 5, "HABILES", "MAS_IGV", null, null, null, lineas, true), log));

		OrdenDto oc = directas.crear(new CrearOrdenDirectaRequest(prov, "PEN", "CONTADO", "ALMACEN_CLIENTE",
				null, 5, "HABILES", "MAS_IGV", null, ceco("ADM"), null, lineas, true), log);
		assertTrue(oc.entregaDirecta());
		ordenCompra.liberar(oc.orderId(), new com.tdem.logistica.modules.compras.dto.LiberarOrdenRequest(null), log);
		EntregaDirectaDto e = entregas.obtener(oc.orderId(), log);
		assertEquals(1, e.destinos().size());
		EntregaDirectaDto.Linea l = e.destinos().get(0).lineas().get(0);
		assertNull(l.requestItemId(), "sin requerimiento: va al CECO de la orden");
		EntregaDirectaDto r = entregas.registrar(oc.orderId(), new RegistrarEntregaDirectaRequest(ceco("ADM"),
				guia("D-1"), "https://drive.test/constancia.pdf", null,
				List.of(new Linea(l.orderItemId(), null, null, new BigDecimal("2"), null))), null, log);
		assertEquals("RECEPCION_COMPLETA", r.estado());
	}

	// ------------------------------------------------------------------

	private OrdenDto marcar(UUID orderId, boolean directa, UsuarioPrincipal quien) {
		return ordenCompra.editarCondiciones(orderId,
				new CondicionesOrdenRequest("MAS_IGV", 5, "CALENDARIO", null, null, directa), quien);
	}

	private EntregaDirectaDto registrar(UUID orderId, UUID ceco, GuiaInput guia, UUID linea, UUID need, UUID item,
										String qty, IncidenciaInput incidencia, UsuarioPrincipal quien) {
		return entregas.registrar(orderId, new RegistrarEntregaDirectaRequest(ceco, guia,
				"https://drive.test/constancia.pdf", null,
				List.of(new Linea(linea, need, item, new BigDecimal(qty), incidencia))), null, quien);
	}

	private static GuiaInput guia(String numero) {
		return new GuiaInput("T001", numero, LocalDate.now());
	}

	private static EntregaDirectaDto.Linea linea(EntregaDirectaDto e, UUID ceco) {
		return e.destinos().stream().filter(d -> d.cecoId().equals(ceco)).findFirst().orElseThrow().lineas().get(0);
	}

	private UUID ceco(String codigo) {
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				 where e.code = 'TDEMSRL' and c.code = ?""", UUID.class, codigo);
	}

	private String nombreCeco(UUID ceco) {
		return jdbc.queryForObject("select name from ceco where ceco_id = ?", String.class, ceco);
	}

	private UUID requerimiento(String codigo, UUID ceco, UUID dueno) {
		return jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				select ?, company_id, ceco_id, ?, 'INTERNO', 'CONSUMO', 'prueba', 'EN_ATENCION', 'APROVISIONAMIENTO'
				  from ceco where ceco_id = ? returning request_id""", UUID.class, codigo, dueno, ceco);
	}

	private UUID item(UUID req, UUID producto, UUID variante, int qty) {
		return jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, ?, ?, base_uom_id, ? from producto where product_id = ? returning request_item_id""",
				UUID.class, req, producto, variante, qty, producto);
	}

	private UUID consolidado(UUID producto, UUID variante, int qty) {
		return jdbc.queryForObject("""
				insert into consolidacion (consolidation_code, company_id, product_id, variant_id, uom_id, total_qty, status)
				select 'CON-DIR', e.company_id, p.product_id, ?, p.base_uom_id, ?, 'EN_COTIZACION'
				  from empresa e, producto p where e.code = 'TDEMSRL' and p.product_id = ? returning consolidation_id""",
				UUID.class, variante, qty, producto);
	}

	private UUID necesidad(UUID item, UUID producto, UUID variante, int qty, UUID consolidado) {
		return jdbc.queryForObject("""
				insert into necesidad_compra (request_item_id, company_id, product_id, variant_id, uom_id, qty, status,
				  consolidation_id)
				select ?, e.company_id, p.product_id, ?, p.base_uom_id, ?, 'COMPRA', ?
				  from empresa e, producto p where e.code = 'TDEMSRL' and p.product_id = ? returning need_id""",
				UUID.class, item, variante, qty, consolidado, producto);
	}

	private UUID orden(UUID consolidado, UUID producto, UUID variante, int qty, UUID usuario) {
		UUID prov = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20100000090', 'Prov 90') "
				+ "returning supplier_id", UUID.class);
		UUID orden = jdbc.queryForObject("""
				insert into orden_abastecimiento (order_code, company_id, supplier_id, order_type, payment_condition,
				  delivery_mode, currency_code, subtotal, total, status, created_by)
				select 'OC-DIR', company_id, ?, 'OC_DIRECTA', 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', ?, ?, 'POR_APROBAR', ?
				  from empresa where code = 'TDEMSRL' returning order_id""",
				UUID.class, prov, qty * 5, qty * 5, usuario);
		jdbc.update("""
				insert into orden_item (order_id, consolidation_id, product_id, variant_id, description, uom_id,
				  qty_ordered, unit_cost, line_subtotal, line_total)
				select ?, ?, ?, ?, 'Bolso porta guante', base_uom_id, ?, 5, ?, ? from producto where product_id = ?""",
				orden, consolidado, producto, variante, qty, qty * 5, qty * 5, producto);
		return orden;
	}

	private String estado(UUID req) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, req);
	}

	private String estadoOrden(UUID orderId) {
		return jdbc.queryForObject("select status from orden_abastecimiento where order_id = ?", String.class, orderId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
