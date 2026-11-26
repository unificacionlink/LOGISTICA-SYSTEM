package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RecepcionOrdenDto;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComparativoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.DocumentosOrdenService;
import com.tdem.logistica.modules.compras.OrdenCompraService;
import com.tdem.logistica.modules.compras.VerificacionOcService;
import com.tdem.logistica.documentos.DocumentosService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 17 (DEC-L061): la OC nace por aprobar; la decide Gerencia o Logistica
 * (Admin de respaldo) y nunca quien la emitio; el rechazo la anula y devuelve
 * sus consolidados a cotizacion; el pago es control (no frena la recepcion,
 * impide anular); el almacen solo recibe una OC liberada.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class OrdenCompraAprobacionTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private DocumentosOrdenService documentosOrden;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComparativoService comparativo;
	@Autowired private ComprasService compras;
	@Autowired private OrdenCompraService ordenCompra;
	@Autowired private RecepcionService recepcion;
	@Autowired private VerificacionOcService verificaciones;
	@Autowired private DocumentosService documentos;

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
	void aprobarPagarLiberarYRecibir() {
		UsuarioPrincipal log = logistica();
		UsuarioPrincipal ger = gerencia();
		OrdenDto oc = emitir("AC01003-00", 3, "20100000071");

		// Nace por aprobar, con su aprobacion pendiente y el almacen bloqueado.
		assertEquals("POR_APROBAR", oc.status());
		assertEquals("PENDIENTE", oc.paymentStatus());
		assertNull(oc.approvedAt());
		assertEquals("Falta aprobar la orden de compra.", oc.bloqueoRecepcion());
		assertEquals("PENDIENTE", estadoAprobacion(oc.orderId()));
		ApiException sinAprobar = assertThrows(ApiException.class, () -> recibir(oc.orderId(), 1));
		assertTrue(sinAprobar.getMessage().contains("aprobar"), sinAprobar.getMessage());

		// Quien la emitio no la ve por decidir ni la puede decidir; el solicitante no entra.
		assertTrue(ordenCompra.listarPorAprobar(log).stream().noneMatch(o -> o.orderId().equals(oc.orderId())));
		assertTrue(ordenCompra.listarPorAprobar(ger).stream().anyMatch(o -> o.orderId().equals(oc.orderId())));
		assertThrows(ApiException.class, () -> ordenCompra.listarPorAprobar(solicitante()));
		assertThrows(ApiException.class, () -> ordenCompra.decidir(oc.orderId(), aprobar(), log));
		assertThrows(ApiException.class, () -> ordenCompra.decidir(oc.orderId(), aprobar(), solicitante()));
		// DEC-L100: si quien la emitio es ADMIN, si la ve por decidir.
		UsuarioPrincipal logAdmin = new UsuarioPrincipal(log.userId(), List.of("LOGISTICA", "ADMIN"));
		assertTrue(ordenCompra.listarPorAprobar(logAdmin).stream().anyMatch(o -> o.orderId().equals(oc.orderId())));
		// Ni la bandeja de REQ decide una OC.
		UUID approvalId = jdbc.queryForObject("select approval_id from aprobacion where entity_id = ?", UUID.class,
				oc.orderId());
		assertThrows(ApiException.class, () -> aprobacionService.decidir(approvalId,
				new DecidirAprobacionRequest("APROBADO", null, null), admin()));

		// Antes de aprobar no hay pago ni liberacion; el rechazo exige motivo.
		UUID constancia = constancia("CONSTANCIA_PAGO");
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("ANTICIPO", constancia, null, null, null, null), log));
		assertThrows(ApiException.class, () -> ordenCompra.liberar(oc.orderId(), new LiberarOrdenRequest(null), log));
		assertThrows(ApiException.class, () -> ordenCompra.decidir(oc.orderId(),
				new DecidirOrdenRequest("RECHAZADO", " "), ger));

		// Gerencia aprueba: EMITIDA y, desde DEC-L094, ya liberada para recibir.
		OrdenDto aprobada = ordenCompra.decidir(oc.orderId(), aprobar(), ger);
		assertEquals("EMITIDA", aprobada.status());
		assertNotNull(aprobada.approvedAt());
		assertNotNull(aprobada.releasedAt());
		assertNull(aprobada.bloqueoRecepcion());
		assertEquals("APROBADO", estadoAprobacion(oc.orderId()));
		assertEquals(ger.userId(), jdbc.queryForObject("select decided_by from aprobacion where entity_id = ?",
				UUID.class, oc.orderId()));
		assertThrows(ApiException.class, () -> ordenCompra.decidir(oc.orderId(), aprobar(), admin()));

		// Anticipo: exige constancia del tipo correcto; con pago registrado no se anula.
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("ANTICIPO", null, null, null, null, null), log));
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("ANTICIPO", constancia("EVIDENCIA"), null, null, null, null), log));
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("PENDIENTE", constancia, null, null, null, null), log));
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("ANTICIPO", constancia, null, null, null, null), ger), "el pago lo registra Logistica");
		OrdenDto anticipo = ordenCompra.registrarPago(oc.orderId(), new RegistrarPagoRequest("ANTICIPO", constancia, null, null, null, null), log);
		assertEquals("ANTICIPO", anticipo.paymentStatus());
		assertNotNull(anticipo.paymentProofUrl());
		ApiException conPago = assertThrows(ApiException.class, () -> compras.anularOrden(oc.orderId(), "Prueba", log));
		assertTrue(conPago.getMessage().contains("pago"), conPago.getMessage());

		// Ya liberada: no se vuelve a liberar y el almacen recibe sin exigir el pago.
		assertThrows(ApiException.class, () -> ordenCompra.liberar(oc.orderId(), null, log));
		RecepcionOrdenDto recibida = recibir(oc.orderId(), 1);
		assertEquals("RECEPCION_PARCIAL", recibida.orden().status());

		// Pago total despues de recibir: el pago nunca depende de la recepcion ni al reves.
		// DEC-L089: el pago final exige la factura (número serie-número y archivo).
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("PAGADO", constancia("CONSTANCIA_PAGO"), null, null, null, null), log), "sin factura");
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("PAGADO", constancia("CONSTANCIA_PAGO"), "123", constancia("FACTURA_PDF"), null, null), log),
				"número sin serie");
		OrdenDto pagada = ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("PAGADO", constancia("CONSTANCIA_PAGO"), "f001-123", constancia("FACTURA_PDF"), null, null), log);
		assertEquals("PAGADO", pagada.paymentStatus());
		assertEquals("F001-123", pagada.invoiceNumber());
		assertNotNull(pagada.invoiceUrl());
		assertThrows(ApiException.class, () -> ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("PAGADO", constancia("CONSTANCIA_PAGO"), "F001-124", constancia("FACTURA_PDF"), null, null), log));
		// Documentos de la OC: la constancia del anticipo, la del pago final, la factura y la recepcion.
		List<DocumentoOrdenDto> docs = documentosOrden.listar(oc.orderId(), log);
		assertEquals(3, docs.stream().filter(d -> "PAGO".equals(d.tipo())).count(), docs.toString());
		assertTrue(docs.stream().anyMatch(d -> d.titulo().equals("Factura F001-123")), docs.toString());
		assertTrue(docs.stream().anyMatch(d -> "RECEPCION".equals(d.tipo()) && d.ruta() != null), docs.toString());

		assertEquals(3, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'ORDEN_ABASTECIMIENTO' and entidad_id = ?
				  and accion in ('APROBAR', 'PAGO', 'LIBERAR')""", Integer.class, oc.orderId()));
	}

	@Test
	void rechazarDevuelveACotizacionYAdminRespalda() {
		OrdenDto oc = emitir("AC01002-00", 2, "20100000072");
		UUID consolidado = jdbc.queryForObject("select consolidation_id from orden_item where order_id = ?",
				UUID.class, oc.orderId());

		OrdenDto rechazada = ordenCompra.decidir(oc.orderId(),
				new DecidirOrdenRequest("RECHAZADO", "Precio fuera de mercado"), gerencia());
		assertEquals("ANULADA", rechazada.status());
		assertNull(rechazada.bloqueoRecepcion());
		assertEquals("RECHAZADO", estadoAprobacion(oc.orderId()));
		assertEquals("Precio fuera de mercado", jdbc.queryForObject(
				"select decision_reason from aprobacion where entity_id = ?", String.class, oc.orderId()));
		assertEquals("EN_COTIZACION", jdbc.queryForObject(
				"select status from consolidacion where consolidation_id = ?", String.class, consolidado));
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'ORDEN_ABASTECIMIENTO' and entidad_id = ?
				  and accion = 'RECHAZAR' and despues ->> 'motivo' = 'Precio fuera de mercado'""",
				Integer.class, oc.orderId()));

		// Se vuelve a adjudicar y emitir; Admin decide de respaldo.
		UUID linea = jdbc.queryForObject("""
				select ci.quotation_item_id from cotizacion_item ci join rfq_item ri on ri.rfq_item_id = ci.rfq_item_id
				 where ri.consolidation_id = ?""", UUID.class, consolidado);
		UUID cotizacion = jdbc.queryForObject("select quotation_id from cotizacion_item where quotation_item_id = ?",
				UUID.class, linea);
		comparativo.adjudicar(new AdjudicarComparativoRequest(List.of(consolidado),
				List.of(new AdjudicarComparativoRequest.Eleccion(consolidado, linea))), logistica());
		OrdenDto otra = abastecimiento.emitirOrden(cotizacion, logistica());
		assertEquals("POR_APROBAR", otra.status());
		assertEquals("EMITIDA", ordenCompra.decidir(otra.orderId(), aprobar(), admin()).status());
	}

	/** DEC-L102: F-LOG-08 una vez recibido todo; DEC-L101: Logistica recibe el correo del REQ aprobado. */
	@Test
	void verificacionFLog08AlRecibirTodo() {
		UsuarioPrincipal log = logistica();
		OrdenDto oc = emitir("AC01001-00", 2, "20100000073");
		assertTrue(jdbc.queryForObject("""
				select count(*) from correo_saliente where event_type = 'REQ_APROBADO' and to_email = 'logistica@logistica.test'""",
				Integer.class) > 0, "Logistica recibe el correo del REQ aprobado");

		ordenCompra.decidir(oc.orderId(), aprobar(), gerencia());
		VerificacionOcDto antes = verificaciones.obtener(oc.orderId(), log);
		assertFalse(antes.disponible(), "sin recibir no se verifica");
		assertFalse(antes.puedeEditar());
		GuardarVerificacionRequest pedido = new GuardarVerificacionRequest(usuario("solicitante@logistica.test"),
				"CONFORME", List.of(new GuardarVerificacionRequest.Item(antes.items().get(0).orderItemId(), null, null,
				"Y", null)));
		assertThrows(ApiException.class, () -> verificaciones.guardar(oc.orderId(), pedido, log));

		assertEquals("RECEPCION_COMPLETA", recibir(oc.orderId(), 2).orden().status());
		VerificacionOcDto lista = verificaciones.obtener(oc.orderId(), log);
		assertTrue(lista.disponible());
		assertTrue(lista.puedeEditar());
		assertEquals(0, lista.items().get(0).recibida().compareTo(BigDecimal.valueOf(2)));
		assertEquals(usuario("solicitante@logistica.test"), lista.especialistaSugeridoId(), "el solicitante del REQ");
		assertFalse(verificaciones.obtener(oc.orderId(), gerencia()).puedeEditar(), "Gerencia la ve, no la registra");
		assertThrows(ApiException.class, () -> verificaciones.guardar(oc.orderId(), pedido, gerencia()));
		assertThrows(ApiException.class, () -> verificaciones.obtener(oc.orderId(), solicitante()));
		assertThrows(ApiException.class, () -> verificaciones.guardar(oc.orderId(), new GuardarVerificacionRequest(
				usuario("solicitante@logistica.test"), "CONFORME", List.of()), log), "todas las lineas");

		VerificacionOcDto guardada = verificaciones.guardar(oc.orderId(), pedido, log);
		assertTrue(guardada.registrada());
		assertEquals(log.userId(), guardada.verificadaPor().id());
		assertEquals("Y", guardada.items().get(0).especificaciones());
		assertNull(guardada.items().get(0).funcionalidad(), "no aplica");
		byte[] pdf = documentos.verificacionOc(oc.orderId(), log).contenido();
		assertEquals("%PDF", new String(pdf, 0, 4));
		assertTrue(verificaciones.deNota(UUID.randomUUID()).isEmpty());
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'ORDEN_COMPRA' and entidad_id = ?
				  and accion = 'VERIFICAR_F_LOG_08'""", Integer.class, oc.orderId()));
	}

	// ---------------------------------------------------------------- apoyo

	/** REQ aprobado sin stock -> consolidado -> una oferta -> adjudicado -> OC emitida por Logistica. */
	private OrdenDto emitir(String variante, int qty, String ruc) {
		UsuarioPrincipal log = logistica();
		UUID req = requerimiento(Map.of(variante, qty), usuario("solicitante@logistica.test"));
		UUID approvalId = jdbc.queryForObject(
				"select approval_id from aprobacion where entity_id = ? and status = 'PENDIENTE'", UUID.class, req);
		aprobacionService.decidir(approvalId, new DecidirAprobacionRequest("APROBADO", null, null), admin());
		UUID cons = abastecimiento.listarConsolidados(log).stream()
				.filter(c -> variante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow().consolidationId();
		UUID prov = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values (?, 'Prov OC') returning supplier_id",
				UUID.class, ruc);
		ComparativoDto c = comparativo.registrar(new RegistrarComparativoRequest(prov, List.of(cons), null, null, null, "PEN",
				"CONTADO", "AGENCIA", 3, null, null, false,
				List.of(new RegistrarComparativoRequest.Precio(cons, new BigDecimal("20"), null, null, null, null, null))), log);
		UUID item = c.proveedores().get(0).precios().get(0).quotationItemId();
		comparativo.adjudicar(new AdjudicarComparativoRequest(List.of(cons),
				List.of(new AdjudicarComparativoRequest.Eleccion(cons, item))), log);
		return abastecimiento.emitirOrden(c.proveedores().get(0).quotationId(), log);
	}

	private RecepcionOrdenDto recibir(UUID orderId, int qty) {
		RecepcionOrdenDto ro = recepcion.obtenerRecepcionOrden(orderId, logistica());
		return recepcion.registrar(orderId, new RegistrarRecepcionRequest(List.of(new RecepcionItemInput(
				ro.items().get(0).orderItemId(), BigDecimal.valueOf(qty), "CONFORME", null, null)), null, null), logistica());
	}

	private static DecidirOrdenRequest aprobar() {
		return new DecidirOrdenRequest("APROBADO", null);
	}

	private String estadoAprobacion(UUID orderId) {
		return jdbc.queryForObject("select status from aprobacion where entity_type = 'COMPRA' and entity_id = ?",
				String.class, orderId);
	}

	private UUID constancia(String tipo) {
		return jdbc.queryForObject("insert into documento (document_type, file_name, file_url) values (?, 'pago.pdf', 'https://x/pago.pdf') returning document_id",
				UUID.class, tipo);
	}

	/** REQ de TDEM pendiente de aprobacion, con su aprobacion. */
	private UUID requerimiento(Map<String, Integer> pedido, UUID requester) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'APROVISIONAMIENTO') returning request_id""",
				UUID.class, "REQ-" + UUID.randomUUID().toString().substring(0, 8), empresa, cecoId, requester);
		pedido.forEach((v, q) -> jdbc.update("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ?""", reqId, q, v));
		jdbc.update("insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?)", reqId);
		return reqId;
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private UsuarioPrincipal gerencia() {
		return new UsuarioPrincipal(usuario("gerencia@logistica.test"), List.of("GERENCIA"));
	}

	private UsuarioPrincipal admin() {
		return new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
	}

	private UsuarioPrincipal solicitante() {
		return new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
