package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.dto.CrearNotaRequest;
import com.tdem.logistica.modules.almacen.dto.DespachoItemInput;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.cajachica.dto.*;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.tracking.TrackingService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 9 (DEC-L026): Caja Chica como ruta de compra del requerimiento. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class CajaChicaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private CajaChicaService cajaChica;
	@Autowired private ComprasService comprasService;
	@Autowired private DespachoService despachoService;
	@Autowired private TrackingService trackingService;

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
	void sugiereRegistraReservaDespachaYRegulariza() {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UsuarioPrincipal admin = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));

		// REQ urgente para hoy: 5 u. de AC01001 y 3 u. de AC01002, sin stock ni costo historico.
		UUID req = crearReq("REQ-TEST-CC1", "URGENTE", LocalDate.now(ZoneId.of("America/Lima")));
		UUID item1 = crearItem(req, "AC01001", "AC01001-00", 5);
		UUID item2 = crearItem(req, "AC01002", "AC01002-00", 3);

		// --- Sugerencia ---
		assertThrows(ApiException.class, () -> cajaChica.evaluar(req, solicitante));
		EvaluacionCajaChicaDto ev = cajaChica.evaluar(req, logistica);
		assertEquals(0, ev.regla().umbral().compareTo(new BigDecimal("100")), "umbral referencial PD-05");
		assertNull(ev.estimado(), "sin costo de referencia no se estima");
		assertEquals(List.of("MISMO_DIA"), ev.motivos().stream().map(EvaluacionCajaChicaDto.Motivo::codigo).toList());

		// --- Bajo monto no aplica si el total supera el umbral ---
		ApiException sobreUmbral = assertThrows(ApiException.class, () -> cajaChica.registrar(req,
				compra("BAJO_MONTO", null, "F001", "100", item1, "5", "30"), logistica));
		assertTrue(sobreUmbral.getMessage().contains("supera el umbral"), sobreUmbral.getMessage());

		// --- Compra el mismo dia: entra al almacen y queda reservada ---
		CajaChicaDto c1 = cajaChica.registrar(req, compra("MISMO_DIA", null, "F001", "123", item1, "5", "10"), logistica);
		assertEquals("CC-1-TDEMSRL", c1.codigo());
		assertEquals(0, c1.monto().compareTo(new BigDecimal("50")));
		assertEquals("PENDIENTE_REGULARIZACION", c1.estado());
		assertEquals(0, pendiente(req, item1, logistica).signum(), "ya no esta pendiente de compra");
		assertEquals(0, new BigDecimal("5").compareTo(jdbc.queryForObject(
				"select sum(qty_reserved) from reserva_stock where request_item_id = ? and origin = 'CAJA_CHICA' and status = 'RESERVADA'",
				BigDecimal.class, item1)));
		assertEquals(1, jdbc.queryForObject(
				"select count(*) from movimiento_inventario where reference_type = 'CAJA_CHICA' and movement_type = 'ENTRADA'", Integer.class));

		// --- Comprobante duplicado (RF-029) y exceso sobre lo pendiente ---
		ApiException dup = assertThrows(ApiException.class, () -> cajaChica.registrar(req,
				compra("MISMO_DIA", null, "F001", "000123", item2, "2", "25"), logistica));
		assertTrue(dup.getMessage().contains("CC-1-TDEMSRL"), dup.getMessage());
		assertThrows(ApiException.class, () -> cajaChica.registrar(req,
				compra("MISMO_DIA", null, "F001", "999", item1, "1", "10"), logistica), "item1 ya no tiene pendiente");

		// --- Excepcion: exige sustento ---
		assertThrows(ApiException.class, () -> cajaChica.registrar(req,
				compra("EXCEPCION_AUTORIZADA", null, "B002", "7", item2, "3", "20"), logistica));
		CajaChicaDto c2 = cajaChica.registrar(req,
				compra("EXCEPCION_AUTORIZADA", "Autorizo Gerencia por parada de obra", "B002", "7", item2, "3", "20"), logistica);
		assertEquals("CC-2-TDEMSRL", c2.codigo());
		assertTrue(comprasService.listarPendientes(logistica).stream().noneMatch(r -> r.id().equals(req)),
				"el REQ sale de Pendientes de compra");

		// --- Despacho: la NI se valoriza con el costo de la Caja Chica ---
		NotaInventarioDetalleDto ni = despachoService.crearNotaInventario(req,
				new CrearNotaRequest(List.of(new DespachoItemInput(item1, new BigDecimal("5"))), 1, null, null), logistica);
		assertEquals(0, ni.nota().totalValueSnapshot().compareTo(new BigDecimal("50")));

		// --- Tracking ---
		assertTrue(trackingService.obtener(req, logistica).eventos().stream()
				.anyMatch(e -> "CC-1-TDEMSRL".equals(e.referencia()) && e.accion().contains("Caja Chica")));

		// --- Bajo monto con costo historico (el de la Caja Chica anterior: 2 x 10 = 20) ---
		UUID req2 = crearReq("REQ-TEST-CC2", "NORMAL", LocalDate.now().plusDays(10));
		UUID item3 = crearItem(req2, "AC01001", "AC01001-00", 2);
		EvaluacionCajaChicaDto ev2 = cajaChica.evaluar(req2, logistica);
		assertEquals(0, ev2.estimado().compareTo(new BigDecimal("20")));
		assertEquals(List.of("BAJO_MONTO"), ev2.motivos().stream().map(EvaluacionCajaChicaDto.Motivo::codigo).toList());
		assertThrows(ApiException.class, () -> cajaChica.registrar(req2,
				compra("MISMO_DIA", null, "T1", "1", item3, "2", "10"), logistica), "no es urgente");
		cajaChica.registrar(req2, compra("BAJO_MONTO", null, "T1", "1", item3, "2", "10"), logistica);

		// --- Regularizacion ---
		assertEquals(3, cajaChica.listar(logistica).size());
		assertThrows(ApiException.class, () -> cajaChica.observar(c1.id(), " ", logistica));
		assertEquals("OBSERVADO", cajaChica.observar(c1.id(), "Falta el voucher", logistica).estado());
		CajaChicaDto reg = cajaChica.regularizar(c1.id(), "MOV-8891", logistica);
		assertEquals("REGULARIZADO", reg.estado());
		assertEquals("MOV-8891", reg.codigoExterno());
		assertNull(reg.observacion());
		assertThrows(ApiException.class, () -> cajaChica.regularizar(c1.id(), null, logistica));

		// --- Regla editable solo por ADMIN (RF-026) ---
		GuardarReglaRequest nueva = new GuardarReglaRequest(new BigDecimal("250"), true, true, false, true);
		assertThrows(ApiException.class, () -> cajaChica.guardarRegla(nueva, logistica));
		ReglaCajaChicaDto regla = cajaChica.guardarRegla(nueva, admin);
		assertEquals(0, regla.umbral().compareTo(new BigDecimal("250")));
		assertFalse(regla.fallaProveedor());
	}

	// ------------------------------------------------------------------

	private static RegistrarCajaChicaRequest compra(String motivo, String detalle, String serie, String numero,
													UUID requestItemId, String qty, String costo) {
		return new RegistrarCajaChicaRequest(motivo, detalle, LocalDate.now(ZoneId.of("America/Lima")), "20555666777",
				"Ferreteria El Perno", "FACTURA", serie, numero, "PEN", null, null,
				List.of(new RegistrarCajaChicaRequest.Item(requestItemId, new BigDecimal(qty), new BigDecimal(costo))));
	}

	private BigDecimal pendiente(UUID req, UUID item, UsuarioPrincipal p) {
		return comprasService.obtenerDetalleCompra(req, p).items().stream()
				.filter(i -> i.requestItemId().equals(item)).findFirst().orElseThrow().cantidadPendiente();
	}

	private UUID crearReq(String codigo, String prioridad, LocalDate requerida) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		return jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority, required_date)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba caja chica', 'EN_ATENCION', ?, ?)
				returning request_id""", UUID.class, codigo, empresa, ceco, usuario("solicitante@logistica.test"),
				prioridad, requerida);
	}

	private UUID crearItem(UUID req, String sku, String variante, int qty) {
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = ?", UUID.class, sku);
		UUID var = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?", UUID.class, variante);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		return jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, ?) returning request_item_id""", UUID.class, req, producto, var, uom, qty);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
