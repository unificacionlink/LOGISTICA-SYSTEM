package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComparativoService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.requerimientos.BandejaService;
import com.tdem.logistica.modules.requerimientos.dto.AvanceRequerimientoDto;
import com.tdem.logistica.modules.requerimientos.dto.BandejaDto;
import com.tdem.logistica.modules.stock.StockService;
import com.tdem.logistica.modules.stock.dto.AjusteStockRequest;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEC-L048: bandeja operativa. La siguiente accion y la vista de cada REQ salen
 * de los datos reales (reserva, necesidad, consolidado, OC, envio); los conteos
 * coinciden con las filas; el solicitante solo ve lo suyo; avance por producto.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class BandejaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private BandejaService bandeja;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComparativoService comparativo;
	@Autowired private RecepcionService recepcion;
	@Autowired private DespachoService despacho;
	@Autowired private StockService stock;

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
	void accionesVistasConteosYAlcance() {
		UsuarioPrincipal log = logistica();
		stock.registrarAjuste(new AjusteStockRequest(variante("EP01005-00"), new BigDecimal("10"), "Saldo"), log);

		// A: con stock -> DESPACHAR
		UUID a = requerimiento(Map.of("EP01005-00", 4), usuario("solicitante@logistica.test"));
		aprobar(a);
		assertEquals(List.of("DESPACHAR"), codigos(fila(a, log)));
		assertTrue(enVista(a, "atencion", log) && enVista(a, "despacho", log));

		// B: sin stock -> PEDIR_COTIZACION
		UUID b = requerimiento(Map.of("AC01003-00", 3), usuario("solicitante@logistica.test"));
		aprobar(b);
		UUID cons = consolidado("AC01003-00");
		assertEquals("PEDIR_COTIZACION", codigos(fila(b, log)).get(0));
		assertTrue(enVista(b, "compras", log) && enVista(b, "atencion", log));
		assertFalse(enVista(b, "despacho", log));

		// Con una oferta -> ADJUDICAR
		UUID prov = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20100000099', 'Prov') returning supplier_id", UUID.class);
		ComparativoDto c = comparativo.registrar(new RegistrarComparativoRequest(prov, List.of(cons), null, null, null, "PEN",
				"CONTADO", "AGENCIA", 3, null, null, false,
				List.of(new RegistrarComparativoRequest.Precio(cons, new BigDecimal("50"), null, null, null, null, null))), log);
		assertEquals(List.of("ADJUDICAR"), codigos(fila(b, log)));
		assertEquals(cons.toString(), fila(b, log).acciones().get(0).destino());

		// Adjudicado -> EMITIR_OC; emitida -> RECIBIR_OC (espera): pasa a Seguimiento, sigue en Compras
		UUID item = c.proveedores().get(0).precios().get(0).quotationItemId();
		comparativo.adjudicar(new AdjudicarComparativoRequest(List.of(cons),
				List.of(new AdjudicarComparativoRequest.Eleccion(cons, item))), log);
		assertEquals(List.of("EMITIR_OC"), codigos(fila(b, log)));
		OrdenDto oc = abastecimiento.emitirOrden(c.proveedores().get(0).quotationId(), log);
		BandejaDto.Fila fb = fila(b, log);
		assertEquals(List.of("RECIBIR_OC"), codigos(fb));
		assertTrue(fb.acciones().get(0).espera());
		assertTrue(enVista(b, "seguimiento", log) && enVista(b, "compras", log));
		assertFalse(enVista(b, "atencion", log));

		// Recepcion parcial (1 de 3): lo recibido queda listo para despachar
		RecepcionOrdenDto ro = recepcion.obtenerRecepcionOrden(oc.orderId(), log);
		recepcion.registrar(oc.orderId(), new RegistrarRecepcionRequest(List.of(new RecepcionItemInput(
				ro.items().get(0).orderItemId(), BigDecimal.ONE, "CONFORME", null, null)), null, null), log);
		assertEquals(List.of("DESPACHAR", "RECIBIR_OC"), codigos(fila(b, log)));
		AvanceRequerimientoDto.Item ib = bandeja.avance(b, log).items().get(0);
		assertEquals(0, BigDecimal.ONE.compareTo(ib.listo()));
		assertEquals(0, new BigDecimal("2").compareTo(ib.enCompra()));
		assertEquals(0, BigDecimal.ONE.compareTo(ib.recibidoCompra()));
		assertEquals("OC", ib.etapaCompra());
		assertEquals(oc.orderId(), ib.orderId());

		// Despacho con envio de A -> CONFIRMAR_ENTREGA (espera), avance en camino
		DespachoRequerimientoDto d = despacho.obtenerDespacho(a, log);
		NotaInventarioDetalleDto nota = despacho.crearNotaInventario(a, new CrearNotaRequest(
				List.of(new DespachoItemInput(d.items().get(0).requestItemId(), new BigDecimal("4"))), 1, "NUEVO", null), log);
		despacho.registrarEnvio(nota.nota().inventoryNoteId(), new RegistrarEnvioRequest("PROPIO", null, null, 1, null,
				"PEN", BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now(), LocalDate.now()), log);
		BandejaDto.Fila fa = fila(a, log);
		assertEquals(List.of("CONFIRMAR_ENTREGA"), codigos(fa));
		assertEquals(1.0, fa.avance().enCamino(), 0.001);
		assertTrue(enVista(a, "seguimiento", log));

		// C: pendiente de aprobacion -> Seguimiento (espera al aprobador)
		UUID cc = requerimiento(Map.of("EP01006-00", 2), usuario("solicitante@logistica.test"));
		assertEquals(List.of("APROBACION"), codigos(fila(cc, log)));
		assertTrue(enVista(cc, "seguimiento", log));

		// Conteos consistentes con las filas de cada vista
		BandejaDto todo = bandeja.bandeja(null, null, null, 0, log);
		assertEquals("atencion", todo.vista());
		for (BandejaDto.Vista v : todo.vistas()) {
			assertEquals(v.total(), bandeja.bandeja(v.id(), null, null, 0, log).total(), v.id());
		}

		// Busqueda por producto (sin tildes) y empresa
		BandejaDto busq = bandeja.bandeja("compras", null, "cámara endoscopio", 0, log);
		assertEquals(List.of(b), busq.filas().stream().map(BandejaDto.Fila::id).toList());
		assertEquals(0, bandeja.bandeja("seguimiento", "METRIN", null, 0, log).total());

		// Alcance: el solicitante no ve el REQ de otro; sus vistas son las suyas
		UUID ajeno = requerimiento(Map.of("EP01006-00", 1), usuario("admin@logistica.test"));
		UsuarioPrincipal sol = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		BandejaDto suya = bandeja.bandeja("proceso", null, null, 0, sol);
		assertEquals(List.of("pendientes", "proceso", "cerrados"), suya.vistas().stream().map(BandejaDto.Vista::id).toList());
		assertTrue(suya.filas().stream().noneMatch(f -> f.id().equals(ajeno)));
		assertTrue(suya.filas().stream().allMatch(f -> f.acciones().stream().noneMatch(x -> x.codigo().equals("DESPACHAR"))),
				"las acciones de almacen son solo de Logistica");
		assertThrows(ApiException.class, () -> bandeja.avance(ajeno, sol));
	}

	// ------------------------------------------------------------------

	private BandejaDto.Fila fila(UUID id, UsuarioPrincipal p) {
		for (BandejaDto.Vista v : bandeja.bandeja(null, null, null, 0, p).vistas()) {
			for (BandejaDto.Fila f : bandeja.bandeja(v.id(), null, null, 0, p).filas()) {
				if (f.id().equals(id)) {
					return f;
				}
			}
		}
		throw new AssertionError("no esta en ninguna vista");
	}

	private boolean enVista(UUID id, String vista, UsuarioPrincipal p) {
		return bandeja.bandeja(vista, null, null, 0, p).filas().stream().anyMatch(f -> f.id().equals(id));
	}

	private static List<String> codigos(BandejaDto.Fila f) {
		return f.acciones().stream().map(BandejaDto.Accion::codigo).toList();
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private void aprobar(UUID requestId) {
		UUID approvalId = jdbc.queryForObject(
				"select approval_id from aprobacion where entity_id = ? and status = 'PENDIENTE'", UUID.class, requestId);
		aprobacionService.decidir(approvalId, new DecidirAprobacionRequest("APROBADO", null, null),
				new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN")));
	}

	private UUID consolidado(String codigoVariante) {
		return abastecimiento.listarConsolidados(logistica()).stream()
				.filter(c -> codigoVariante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow().consolidationId();
	}

	/** REQ de TDEM pendiente de aprobacion, con su aprobacion. */
	private UUID requerimiento(Map<String, Integer> pedido, UUID requester) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'NORMAL') returning request_id""",
				UUID.class, "REQ-" + UUID.randomUUID().toString().substring(0, 8), empresa, cecoId, requester);
		pedido.forEach((v, qty) -> jdbc.update("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ?""", reqId, qty, v));
		jdbc.update("insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?)", reqId);
		return reqId;
	}

	private UUID variante(String codigo) {
		return jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?", UUID.class, codigo);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
