package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.PreparacionNotaService;
import com.tdem.logistica.modules.almacen.RecepcionDestinoService;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.dashboard.DashboardDto;
import com.tdem.logistica.modules.dashboard.DashboardController;
import com.tdem.logistica.modules.dashboard.DashboardService;
import com.tdem.logistica.modules.dashboard.DemandaDto;
import com.tdem.logistica.modules.dashboard.DemandaService;
import com.tdem.logistica.modules.dashboard.GastoDto;
import com.tdem.logistica.modules.dashboard.GastoService;
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
	@Autowired private PreparacionNotaService preparacionService;
	@Autowired private RecepcionDestinoService destinoService;
	@Autowired private ComprasService comprasService;
	@Autowired private TrackingService trackingService;
	@Autowired private DashboardService dashboardService;
	@Autowired private DemandaService demandaService;
	@Autowired private GastoService gastoService;
	@Autowired private DashboardController dashboardController;
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

		// --- Preparacion de la NI 1 (ALM-02): picking -> embalaje -> envio ---
		UUID nota1 = ni1.nota().inventoryNoteId();
		RegistrarEnvioRequest agencia = new RegistrarEnvioRequest("AGENCIA", "Shalom", "SH-001", null, null, "PEN",
				new BigDecimal("50"), new BigDecimal("9"), LocalDate.now(), LocalDate.now().plusDays(2), "https://ticket.test/envio");
		ApiException sinPreparar = assertThrows(ApiException.class,
				() -> despachoService.registrarEnvio(nota1, agencia, logistica));
		assertTrue(sinPreparar.getMessage().contains("picking"), sinPreparar.getMessage());
		assertThrows(ApiException.class, () -> preparacionService.embalar(nota1, 2, FOTO, logistica),
				"no se embala sin picking");
		assertThrows(ApiException.class, () -> preparacionService.confirmarPicking(nota1, solicitante),
				"solo LOGISTICA/ADMIN prepara");

		NotaInventarioDetalleDto piqueada = preparacionService.confirmarPicking(nota1, logistica);
		assertNotNull(piqueada.nota().pickedAt());
		assertNull(piqueada.nota().packedAt());
		assertThrows(ApiException.class, () -> preparacionService.confirmarPicking(nota1, logistica),
				"el picking se confirma una vez");
		// DEC-L093: tras el picking basta el enlace del ticket de envio.
		RegistrarEnvioRequest sinTicket = new RegistrarEnvioRequest(null, null, null, null, null, null, null, null, null,
				null, null);
		assertThrows(ApiException.class, () -> despachoService.registrarEnvio(nota1, sinTicket, logistica),
				"el envio exige el enlace del ticket");
		assertThrows(ApiException.class, () -> preparacionService.embalar(nota1, 0, FOTO, logistica),
				"bultos > 0");
		assertThrows(ApiException.class, () -> preparacionService.embalar(nota1, 2, "javascript:alert(1)", logistica),
				"la evidencia es un enlace http(s) (V38.7)");
		assertThrows(ApiException.class, () -> preparacionService.embalar(nota1, 2, null, logistica),
				"la evidencia es obligatoria");

		preparacionService.embalar(nota1, 3, FOTO, logistica);
		NotaInventarioDetalleDto embalada = preparacionService.embalar(nota1, 2, FOTO, logistica);
		assertNotNull(embalada.nota().packedAt());
		assertEquals(2, embalada.nota().packageCount(), "el embalaje se corrige antes del envio");
		assertEquals(FOTO, embalada.nota().packingEvidenceUrl());
		assertEquals(2, jdbc.queryForObject(
				"select count(*) from log_cambios where entidad = 'NOTA_INVENTARIO' and entidad_id = ? and accion = 'EMBALAJE'",
				Integer.class, nota1));

		// --- Envio + confirmacion de la NI 1 -> atencion parcial ---
		NotaInventarioDetalleDto enviada1 = despachoService.registrarEnvio(ni1.nota().inventoryNoteId(),
				agencia, logistica);
		assertEquals("DESPACHADA", enviada1.nota().status());
		assertEquals(2, enviada1.nota().envio().packageCount(), "el envio toma los bultos del embalaje");
		assertEquals("https://ticket.test/envio", enviada1.nota().envio().ticketUrl());
		assertThrows(ApiException.class, () -> preparacionService.embalar(nota1, 4, FOTO, logistica),
				"una NI enviada ya no se re-embala");
		assertEquals(0, enviada1.nota().envio().shippingCostTotal().compareTo(new BigDecimal("59")));
		UUID envio1 = enviada1.nota().envio().shipmentId();

		assertThrows(ApiException.class, () -> recibirTodo(envio1, ni1, solicitante),
				"el solicitante no confirma la recepcion en destino");

		recibirTodo(envio1, ni1, logistica);
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
		// Sin embalaje: de picking directo al envio (DEC-L093), modalidad por defecto.
		preparacionService.confirmarPicking(ni2.nota().inventoryNoteId(), logistica);
		UUID envio2 = despachoService.registrarEnvio(ni2.nota().inventoryNoteId(),
				new RegistrarEnvioRequest(null, null, null, null, null, null, null, null, null, null, "https://ticket.test/envio"),
				logistica).nota().envio().shipmentId();
		NotaInventarioDetalleDto recibida2 = recibirTodo(envio2, ni2, logistica);
		assertEquals("RECEPCIONADA", recibida2.nota().status());
		assertEquals("ATENCION_COMPLETA", estadoRequerimiento(f));

		// --- Recibir otra vez un envio ya recepcionado no esta permitido ---
		assertThrows(ApiException.class, () -> recibirTodo(envio2, ni2, logistica));

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
		// La fecha requerida la fija el servidor al enviar; el fixture inserta el REQ directo.
		jdbc.update("update requerimiento set required_date = current_date + 5 where request_id = ?", f.requestId);
		DashboardDto d = dashboardService.obtener(30, "TDEMSRL", logistica);
		assertTrue(d.cumplimiento().entregados() >= 1 && d.cumplimiento().aTiempo() >= 1, d.cumplimiento().toString());
		assertTrue(d.cumplimiento().conCompra() >= 1, "tuvo una necesidad de compra (DEC-L083)");
		assertTrue(d.completados() >= 1);
		assertNotNull(d.diasCiclo());
		assertTrue(d.rfqEnviadas() >= 2 && d.rfqRespondidas() >= 2, d.toString());
		assertEquals(5, d.etapas().size());
		DashboardDto.Comparativo comp = d.comparativos().stream()
				.filter(c -> c.codigo().equals("CON-TEST-ALM")).findFirst().orElseThrow();
		assertEquals(2, comp.cotizaciones());
		assertEquals(0, comp.ahorro().compareTo(new BigDecimal("20")), "70 cotizado - 50 elegido");
		assertTrue(d.ahorro().stream().anyMatch(a -> a.moneda().equals("PEN") && a.monto().signum() > 0));
		assertTrue(dashboardService.obtener(30, "METRIN", logistica).comparativos().stream()
				.noneMatch(c -> c.codigo().equals("CON-TEST-ALM")), "el filtro de empresa excluye a TDEM");
		assertThrows(ApiException.class, () -> dashboardService.obtener(30, null, solicitante),
				"el solicitante no ve el dashboard");

		// --- Demanda (DEC-L083): la linea del REQ, la matriz ABC de lo despachado y el Excel ---
		DemandaDto dem = demandaService.obtener(30, "TDEMSRL", null, logistica);
		assertTrue(dem.lineas() >= 1 && dem.requerimientos() >= 1);
		assertTrue(dem.detalle().stream().anyMatch(l -> l.requerimiento().equals("REQ-TEST-ALM")
				&& l.sku().equals("AC01001-00") && l.cantidad().compareTo(new BigDecimal("12")) == 0));
		assertTrue(dem.productos().stream().anyMatch(p -> p.sku().equals("AC01001")));
		DemandaDto.ProductoAbc abc = dem.abc().productos().stream()
				.filter(p -> p.sku().equals("AC01001")).findFirst().orElseThrow();
		assertEquals(0, abc.unidades().compareTo(new BigDecimal("12")), "9 + 3 despachadas");
		assertEquals(0, abc.valor().compareTo(new BigDecimal("60")), "12 u. x S/ 5 de la ultima OC");
		assertEquals("A", abc.clase());
		assertEquals(3, dem.abc().clases().size());
		UUID tipo = jdbc.queryForObject("select product_type_id from producto where sku_base = 'AC01001'", UUID.class);
		assertTrue(demandaService.obtener(30, "TDEMSRL", "t:" + tipo, logistica).lineas() >= 1, "su familia la incluye");
		assertEquals(0, demandaService.obtener(30, "TDEMSRL", "f:" + UUID.randomUUID(), logistica).lineas());
		assertThrows(ApiException.class, () -> demandaService.obtener(30, null, "x:1", logistica));
		assertThrows(ApiException.class, () -> demandaService.obtener(30, null, null, solicitante));
		byte[] demandaXlsx = dashboardController.demandaExcel(30, "TDEMSRL", null, logistica).getBody();
		try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(demandaXlsx))) {
			var hoja = wb.getSheet("DEMANDA");
			assertEquals(10, hoja.getRow(0).getLastCellNum());
			assertTrue(hoja.getLastRowNum() >= 1);
		} catch (IOException e) {
			throw new AssertionError(e);
		}

		// --- Gasto (DEC-L083): la OC de S/ 50 sin IGV, al CECO del REQ via el consolidado ---
		GastoDto g = gastoService.obtener(30, "TDEMSRL", logistica);
		assertTrue(g.monedas().stream().anyMatch(m -> m.moneda().equals("PEN") && m.monto().compareTo(new BigDecimal("50")) >= 0));
		assertTrue(g.porCeco().stream().anyMatch(c -> "ADM".equals(c.clave()) && c.monto().compareTo(new BigDecimal("50")) >= 0),
				g.porCeco().toString());
		assertTrue(g.porEmpresa().stream().anyMatch(e -> e.clave().equals("TDEMSRL")));
		assertFalse(g.porFamilia().isEmpty());
		assertFalse(g.meses().isEmpty());
		assertTrue(gastoService.obtener(30, "METRIN", logistica).porEmpresa().stream()
				.noneMatch(e -> e.clave().equals("TDEMSRL")), "el filtro de empresa excluye a TDEM");
		assertThrows(ApiException.class, () -> gastoService.obtener(30, null, solicitante));

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
		// Lote 16 (DEC-L039): el faltante (10) es una necesidad dentro de un consolidado.
		UUID consolidado = jdbc.queryForObject("""
				insert into consolidacion (consolidation_code, company_id, product_id, variant_id, uom_id, total_qty, status)
				values ('CON-TEST-ALM', ?, ?, ?, ?, 10, 'EN_COTIZACION') returning consolidation_id""",
				UUID.class, empresa, producto, variante, uom);
		jdbc.update("""
				insert into necesidad_compra (request_item_id, company_id, product_id, variant_id, uom_id, qty, status,
				  consolidation_id) values (?, ?, ?, ?, ?, 10, 'COMPRA', ?)""", item, empresa, producto, variante, uom, consolidado);
		UUID rfqItem = jdbc.queryForObject("""
				insert into rfq_item (rfq_id, consolidation_id, product_id, variant_id, uom_id, qty)
				values (?, ?, ?, ?, ?, 10) returning rfq_item_id""", UUID.class, rfq, consolidado, producto, variante, uom);
		UUID cotizacion = jdbc.queryForObject("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
				values (?, ?, 'PEN', 50, 50, 'SELECCIONADA') returning quotation_id""", UUID.class, rfq, proveedor);
		UUID ganadora = jdbc.queryForObject("""
				insert into cotizacion_item (quotation_id, rfq_item_id, product_id, variant_id, qty, unit_cost, line_total)
				values (?, ?, ?, ?, 10, 5, 50) returning quotation_item_id""", UUID.class, cotizacion, rfqItem, producto, variante);
		jdbc.update("update consolidacion set status = 'ORDENADA', awarded_quotation_item_id = ?, awarded_at = now() "
				+ "where consolidation_id = ?", ganadora, consolidado);
		UUID orden = jdbc.queryForObject("""
				insert into orden_abastecimiento (order_code, company_id, supplier_id, quotation_id, payment_condition,
				  delivery_mode, currency_code, subtotal, total, status, created_by, approved_at, released_at)
				values ('OC-TEST-ALM', ?, ?, ?, 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', 50, 50, 'EMITIDA', ?, now(), now())
				returning order_id""", UUID.class, empresa, proveedor, cotizacion, logisticaId);
		// Segunda oferta, mas cara y no elegida (S/ 70): el dashboard mide el ahorro del comparativo.
		UUID proveedor2 = jdbc.queryForObject("""
				insert into proveedor (ruc, legal_name) values ('20987654321', 'Otro Proveedor SAC')
				returning supplier_id""", UUID.class);
		UUID rfq2 = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-TEST-ALM-2', ?, ?, ?, 'CERRADA', ?) returning rfq_id""", UUID.class, empresa, req, proveedor2, logisticaId);
		UUID rfqItem2 = jdbc.queryForObject("""
				insert into rfq_item (rfq_id, consolidation_id, product_id, variant_id, uom_id, qty)
				values (?, ?, ?, ?, ?, 10) returning rfq_item_id""", UUID.class, rfq2, consolidado, producto, variante, uom);
		UUID cotizacion2 = jdbc.queryForObject("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
				values (?, ?, 'PEN', 70, 70, 'RECIBIDA') returning quotation_id""", UUID.class, rfq2, proveedor2);
		jdbc.update("""
				insert into cotizacion_item (quotation_id, rfq_item_id, product_id, variant_id, qty, unit_cost, line_total)
				values (?, ?, ?, ?, 10, 7, 70)""", cotizacion2, rfqItem2, producto, variante);

		UUID ordenItem = jdbc.queryForObject("""
				insert into orden_item (order_id, consolidation_id, product_id, variant_id, description, uom_id,
				  qty_ordered, unit_cost, line_subtotal, line_total)
				values (?, ?, ?, ?, 'Producto de prueba', ?, 10, 5, 50, 50) returning order_item_id""",
				UUID.class, orden, consolidado, producto, variante, uom);

		return new Fixture(req, item, orden, ordenItem, producto, variante);
	}

	private static final String FOTO = "https://drive.test/embalaje.jpg";

	/** Recepcion en destino conforme de todo lo enviado, con el enlace de la conformidad firmada. */
	private NotaInventarioDetalleDto recibirTodo(UUID shipmentId, NotaInventarioDetalleDto ni, UsuarioPrincipal quien) {
		List<RecepcionDestinoRequest.Linea> lineas = ni.items().stream()
				.map(i -> new RecepcionDestinoRequest.Linea(i.issueItemId(), i.qty())).toList();
		return destinoService.registrar(shipmentId, new RecepcionDestinoRequest(UUID.randomUUID(), lineas, "CONFORME",
				null, "https://drive.test/conformidad.pdf"), null, quien);
	}

}
