package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.DiferenciasDestinoService;
import com.tdem.logistica.modules.almacen.PreparacionNotaService;
import com.tdem.logistica.modules.almacen.RecepcionDestinoService;
import com.tdem.logistica.modules.almacen.dto.*;
import com.tdem.logistica.modules.stock.StockService;
import com.tdem.logistica.modules.stock.dto.SaldoStockDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Lote 18b (ALM-03 de la V38.7, DEC-L063): recepcion en destino parcial, conforme
 * y no conforme, con nota firmada o enlace; lo observado se subsana o se devuelve;
 * lo devuelto se recibe en cuarentena en el almacen central. La atencion del
 * requerimiento cuenta solo lo aceptado.
 *
 * Escenario: REQ de 10 u. con 10 u. reservadas; una NI de 10 u. enviada.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class RecepcionDestinoTest {

	private static final String ENLACE = "https://drive.test/conformidad.pdf";
	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private DespachoService despachoService;
	@Autowired private PreparacionNotaService preparacionService;
	@Autowired private RecepcionDestinoService destinoService;
	@Autowired private DiferenciasDestinoService diferenciasService;
	@Autowired private StockService stockService;
	@MockitoBean private StorageClient storage;

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
	void recepcionConDiferenciasSubsanarDevolverYCuarentena() {
		UUID logisticaId = usuario("logistica@logistica.test");
		UUID gerenciaId = usuario("gerencia@logistica.test");
		UsuarioPrincipal logistica = new UsuarioPrincipal(logisticaId, List.of("LOGISTICA"));
		UsuarioPrincipal gerencia = new UsuarioPrincipal(gerenciaId, List.of("GERENCIA"));
		UsuarioPrincipal otraGerencia = new UsuarioPrincipal(UUID.randomUUID(), List.of("GERENCIA"));
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		when(storage.subir(any(), any(), any())).thenReturn("https://storage.test/nota-firmada.pdf");

		UUID requestId = crearRequerimientoReservado(logisticaId, gerenciaId);
		UUID requestItemId = jdbc.queryForObject("select request_item_id from requerimiento_item where request_id = ?",
				UUID.class, requestId);
		NotaInventarioDetalleDto ni = despachoService.crearNotaInventario(requestId, new CrearNotaRequest(
				List.of(new DespachoItemInput(requestItemId, new BigDecimal("10"))), null, null, null), logistica);
		UUID noteId = ni.nota().inventoryNoteId();
		preparacionService.confirmarPicking(noteId, logistica);
		preparacionService.embalar(noteId, 1, "https://drive.test/embalaje.jpg", logistica);
		UUID envio = despachoService.registrarEnvio(noteId, new RegistrarEnvioRequest("PROPIO", null, null, null, null,
				null, null, null, null, null), logistica).nota().envio().shipmentId();
		UUID item = ni.items().get(0).issueItemId();
		assertEquals(1, correos(requestId, "DESPACHO_ENVIADO"), "DEC-L066: el solicitante sabe que salio su pedido");

		// --- Validaciones de opSaveDelivery ---
		assertThrows(ApiException.class, () -> recibir(envio, item, 6, "CONFORME", null, ENLACE, null, solicitante),
				"el solicitante no recibe en destino");
		assertThrows(ApiException.class, () -> recibir(envio, item, 6, "CONFORME", null, ENLACE, null, otraGerencia),
				"GERENCIA solo en los requerimientos de su ambito");
		ApiException sinEvidencia = assertThrows(ApiException.class,
				() -> recibir(envio, item, 6, "CONFORME", null, null, null, logistica));
		assertTrue(sinEvidencia.getMessage().contains("nota firmada"), sinEvidencia.getMessage());
		assertThrows(ApiException.class, () -> recibir(envio, item, 6, "CONFORME", null, "no-es-enlace", null, logistica));
		assertThrows(ApiException.class, () -> recibir(envio, item, 6, "NO_CONFORME", null, ENLACE, null, logistica),
				"no conforme exige observacion");
		assertThrows(ApiException.class, () -> recibir(envio, item, 11, "CONFORME", null, ENLACE, null, logistica),
				"no supera el saldo enviado");
		assertThrows(ApiException.class, () -> recibir(envio, item, 0, "CONFORME", null, ENLACE, null, logistica),
				"al menos una cantidad positiva");

		// --- Recepcion 1: 6 u. conformes (enlace) -> parcial ---
		UUID token = UUID.randomUUID();
		NotaInventarioDetalleDto r1 = destinoService.registrar(envio, new RecepcionDestinoRequest(token,
				List.of(new RecepcionDestinoRequest.Linea(item, new BigDecimal("6"))), "CONFORME", null, ENLACE), null,
				logistica);
		assertEquals("RECEPCION_PARCIAL", r1.nota().envio().status());
		assertEquals("RECEPCION_PARCIAL", r1.nota().status());
		assertEquals("CONFORME", r1.nota().envio().conformityStatus());
		assertEquals(0, new BigDecimal("6").compareTo(r1.items().get(0).destAceptado()));
		assertEquals("ATENCION_PARCIAL", estado(requestId), "la atencion cuenta lo aceptado");
		assertThrows(ApiException.class, () -> destinoService.registrar(envio, new RecepcionDestinoRequest(token,
				List.of(new RecepcionDestinoRequest.Linea(item, new BigDecimal("1"))), "CONFORME", null, ENLACE), null,
				logistica), "el mismo formulario no registra dos recepciones");
		assertThrows(ApiException.class, () -> recibir(envio, item, 5, "CONFORME", null, ENLACE, null, logistica),
				"saldo enviado: quedan 4");

		// --- Recepcion 2: 4 u. no conformes, por GERENCIA de su ambito, con la NI firmada adjunta ---
		NotaInventarioDetalleDto r2 = recibir(envio, item, 4, "NO_CONFORME", "Cajas mojadas", null,
				new MockMultipartFile("file", "ni-firmada.pdf", "application/pdf", new byte[] {1, 2}), gerencia);
		assertEquals("RECEPCIONADO_OBSERVADO", r2.nota().envio().status());
		assertEquals("RECEPCIONADA", r2.nota().status());
		assertEquals("NO_CONFORME", r2.nota().envio().conformityStatus());
		assertEquals(0, new BigDecimal("4").compareTo(r2.items().get(0).destObservado()));
		assertEquals(2, r2.recepciones().size());
		assertEquals(2, correos(requestId, "RECEPCION_REGISTRADA"), "un correo por recepcion al solicitante");
		assertEquals("https://storage.test/nota-firmada.pdf", r2.recepciones().get(1).documentoUrl());
		assertEquals("ATENCION_PARCIAL", estado(requestId), "lo observado no cuenta como atendido");
		assertThrows(ApiException.class, () -> recibir(envio, item, 1, "CONFORME", null, ENLACE, null, logistica),
				"un envio ya recibido completo no admite otra recepcion");

		DestinoDtos.DiferenciasDto dif = diferenciasService.listar(logistica);
		assertEquals(1, dif.observados().stream().filter(o -> o.shipmentId().equals(envio)).count());
		assertThrows(ApiException.class, () -> diferenciasService.listar(solicitante));

		// --- Resolver (aiResolve) ---
		assertThrows(ApiException.class, () -> destinoService.resolver(envio,
				new ResolverObservadoRequest(item, new BigDecimal("5"), "SUBSANAR", ENLACE), logistica), "maximo lo observado");
		assertThrows(ApiException.class, () -> destinoService.resolver(envio,
				new ResolverObservadoRequest(item, BigDecimal.ONE, "SUBSANAR", "sin-enlace"), logistica), "evidencia valida");
		assertThrows(ApiException.class, () -> destinoService.resolver(envio,
				new ResolverObservadoRequest(item, BigDecimal.ONE, "OTRA", ENLACE), logistica));

		NotaInventarioDetalleDto s3 = destinoService.resolver(envio,
				new ResolverObservadoRequest(item, new BigDecimal("3"), "SUBSANAR", ENLACE), logistica);
		assertEquals(0, new BigDecimal("9").compareTo(s3.items().get(0).destAceptado()));
		assertEquals(0, BigDecimal.ONE.compareTo(s3.items().get(0).destObservado()));
		assertEquals("RECEPCIONADO_OBSERVADO", s3.nota().envio().status());

		NotaInventarioDetalleDto d1 = destinoService.resolver(envio,
				new ResolverObservadoRequest(item, BigDecimal.ONE, "DEVOLVER", ENLACE), logistica);
		assertEquals(0, BigDecimal.ONE.compareTo(d1.items().get(0).destDevuelto()));
		assertEquals(0, d1.items().get(0).destObservado().signum());
		assertEquals("RECEPCIONADO_OBSERVADO", d1.nota().envio().status(), "con devolucion no queda todo aceptado");
		assertEquals("NO_CONFORME", d1.nota().envio().conformityStatus());
		assertEquals(List.of("RESUELTA", "EN_TRANSITO"), d1.resoluciones().stream().map(r -> r.estado()).toList());
		assertEquals("ATENCION_PARCIAL", estado(requestId), "9 de 10 aceptadas");

		// --- Retorno al almacen central, en cuarentena (aiReceiveReturn) ---
		UUID devolucion = d1.resoluciones().get(1).resolutionId();
		BigDecimal disponibleAntes = saldo().disponible();
		assertThrows(ApiException.class, () -> destinoService.recibirDevolucion(devolucion,
				new EvidenciaRequest("sin-enlace"), logistica));
		destinoService.recibirDevolucion(devolucion, new EvidenciaRequest("https://drive.test/retorno.jpg"), logistica);
		assertThrows(ApiException.class, () -> destinoService.recibirDevolucion(devolucion,
				new EvidenciaRequest("https://drive.test/retorno.jpg"), logistica), "se recibe una sola vez");
		SaldoStockDto saldo = saldo();
		assertEquals(0, BigDecimal.ONE.compareTo(saldo.enCuarentena()));
		assertEquals(0, disponibleAntes.compareTo(saldo.disponible()), "la cuarentena no vuelve a disponible");
		assertEquals("RECIBIDA_OBSERVADA", diferenciasService.listar(logistica).devoluciones().stream()
				.filter(d -> d.resolutionId().equals(devolucion)).findFirst().orElseThrow().estado());

		assertEquals(4, jdbc.queryForObject(
				"select count(*) from log_cambios where entidad = 'ENVIO' and entidad_id = ?", Integer.class, envio),
				"2 recepciones + subsanar + devolver auditados");
	}

	// ------------------------------------------------------------------

	private NotaInventarioDetalleDto recibir(UUID envio, UUID item, int qty, String resultado, String obs, String enlace,
											 MockMultipartFile archivo, UsuarioPrincipal quien) {
		return destinoService.registrar(envio, new RecepcionDestinoRequest(UUID.randomUUID(),
				List.of(new RecepcionDestinoRequest.Linea(item, BigDecimal.valueOf(qty))), resultado, obs, enlace),
				archivo, quien);
	}

	private SaldoStockDto saldo() {
		return stockService.listarSaldos(new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA")))
				.stream().filter(s -> s.codigoVariante().equals("AC01002-00")).findFirst().orElseThrow();
	}

	private int correos(UUID requestId, String evento) {
		return jdbc.queryForObject("""
				select count(*) from correo_saliente c join usuario u on u.user_id = c.recipient_user_id
				where c.entity_id = ? and c.event_type = ? and u.corporate_email = 'solicitante@logistica.test'""",
				Integer.class, requestId, evento);
	}

	private String estado(UUID requestId) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, requestId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}

	/** REQ de 10 u. en atencion, con 10 u. de stock reservadas y aprobado por GERENCIA (su ambito). */
	private UUID crearRequerimientoReservado(UUID logisticaId, UUID gerenciaId) {
		UUID solicitanteId = usuario("solicitante@logistica.test");
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID producto = jdbc.queryForObject("select product_id from producto where sku_base = 'AC01002'", UUID.class);
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01002-00'", UUID.class);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		UUID almacen = jdbc.queryForObject("select warehouse_id from almacen where company_id = ? and code = 'CENTRAL'",
				UUID.class, empresa);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values ('REQ-TEST-DEST', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba destino', 'EN_ATENCION')
				returning request_id""", UUID.class, empresa, ceco, solicitanteId);
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, 10) returning request_item_id""", UUID.class, req, producto, variante, uom);
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				values (?, ?, ?, ?, 'AJUSTE', 10, 'AJUSTE', gen_random_uuid(), ?)""",
				empresa, almacen, producto, variante, logisticaId);
		jdbc.update("""
				insert into reserva_stock (request_item_id, warehouse_id, product_id, variant_id, qty_reserved, status,
				  reserved_at, origin)
				values (?, ?, ?, ?, 10, 'RESERVADA', now(), 'VERIFICACION_STOCK')""", item, almacen, producto, variante);
		jdbc.update("""
				insert into aprobacion (entity_type, entity_id, status, assigned_to, decided_by, decided_at)
				values ('REQUERIMIENTO', ?, 'APROBADO', ?, ?, now())""", req, gerenciaId, gerenciaId);
		return req;
	}
}
