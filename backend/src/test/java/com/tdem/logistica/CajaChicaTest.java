package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.cajachica.dto.*;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.dto.NecesidadDto;
import com.tdem.logistica.modules.requerimientos.BandejaService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.requerimientos.dto.CrearRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.ItemRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEC-L054: la caja chica es del proyecto. El solicitante la sugiere, Logistica
 * autoriza la ruta, el proyecto compra y el solicitante sube el comprobante; lo
 * comprado no pasa por el almacen y cuenta como atendido al regularizarse.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class CajaChicaTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private CajaChicaService cajaChica;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private AprobacionService aprobaciones;
	@Autowired private BandejaService bandeja;
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
	void elProyectoCompraConSuCajaChicaYLogisticaRegulariza() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UsuarioPrincipal ajeno = principal("pyc@logistica.test", "PYC");

		// --- El solicitante sugiere comprar con la caja chica del proyecto ---
		RequerimientoDto req = requerimientos.crear(new CrearRequerimientoRequest(cecoAdm(), "NORMAL", "CONSUMO",
				"Pilas y cinta para la obra del proyecto", LocalDate.now(LIMA).plusDays(5),
				List.of(item("AC01001-00", 5), item("AC01002-00", 3)), true, "Hay una ferreteria al lado de la obra"),
				solicitante);
		assertTrue(req.cajaChicaSugerida());
		requerimientos.enviar(req.id(), solicitante);
		UUID aprobacion = requerimientos.obtener(req.id(), aprobador).pendingApprovalId();
		aprobaciones.decidir(aprobacion, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
		UUID item1 = itemDe(req.id(), "AC01001-00");
		UUID item2 = itemDe(req.id(), "AC01002-00");

		// --- Llega a Necesidades por decidir, con la nota del solicitante ---
		List<NecesidadDto> necesidades = abastecimiento.listarNecesidades(logistica).stream()
				.filter(n -> n.requestId().equals(req.id())).toList();
		assertEquals(2, necesidades.size());
		assertTrue(necesidades.stream().allMatch(n -> "PENDIENTE_RUTA".equals(n.status())
				&& "SOLICITANTE".equals(n.suggestedReason())
				&& "Hay una ferreteria al lado de la obra".equals(n.notaSolicitante())), necesidades.toString());

		// --- Sin autorizacion de Logistica no se registra ---
		assertFalse(cajaChica.formulario(req.id(), solicitante).puedeRegistrar());
		UUID doc1 = comprobante();
		assertThrows(ApiException.class, () -> cajaChica.registrar(req.id(), compra(item1, "5", "10", "F001", "123", doc1),
				solicitante));

		// --- Logistica autoriza la ruta ---
		necesidades.forEach(n -> abastecimiento.decidirRuta(n.needId(), "CAJA_CHICA", logistica));
		FormularioCajaChicaDto f = cajaChica.formulario(req.id(), solicitante);
		assertTrue(f.puedeRegistrar());
		assertEquals(2, f.items().size());
		assertTrue(f.items().stream().allMatch(i -> i.pendiente().compareTo(i.autorizado()) == 0));
		assertThrows(ApiException.class, () -> cajaChica.formulario(req.id(), ajeno), "solo el dueno o Logistica");
		assertTrue(bandeja.bandeja("pendientes", null, null, 0, solicitante).filas().stream()
				.anyMatch(x -> x.id().equals(req.id())
						&& x.acciones().stream().anyMatch(a -> "REGISTRAR_CAJA_CHICA".equals(a.codigo()) && !a.espera())),
				"el solicitante tiene que registrar la compra");

		// --- El solicitante registra con su comprobante: no entra al almacen ---
		assertThrows(ApiException.class, () -> cajaChica.registrar(req.id(),
				compra(item1, "5", "10", "F001", "123", UUID.randomUUID()), solicitante), "exige el comprobante adjunto");
		CajaChicaDto c1 = cajaChica.registrar(req.id(), compra(item1, "5", "10", "F001", "123", doc1), solicitante);
		assertEquals("PROYECTO", c1.destino());
		assertEquals("PENDIENTE_REGULARIZACION", c1.estado());
		assertEquals("SOLICITANTE", c1.motivo());
		assertEquals(0, c1.monto().compareTo(new BigDecimal("50")));
		assertEquals(0, jdbc.queryForObject("select count(*) from movimiento_inventario where reference_type = 'CAJA_CHICA'",
				Integer.class), "no pasa por el almacen");
		assertEquals(0, jdbc.queryForObject("select count(*) from reserva_stock where origin = 'CAJA_CHICA'", Integer.class));
		assertThrows(ApiException.class, () -> cajaChica.registrar(req.id(),
				compra(item1, "1", "10", "F001", "999", comprobante()), solicitante), "supera lo autorizado");
		ApiException dup = assertThrows(ApiException.class, () -> cajaChica.registrar(req.id(),
				compra(item2, "2", "25", "F001", "000123", comprobante()), solicitante));
		assertTrue(dup.getMessage().contains(c1.codigo()), dup.getMessage());
		assertEquals("EN_ATENCION", estado(req.id()), "aun no cuenta como atendido");

		// --- Observada: el solicitante corrige y vuelve a revision ---
		cajaChica.observar(c1.id(), "La boleta no se lee", logistica);
		UUID doc2 = comprobante();
		assertThrows(ApiException.class, () -> cajaChica.corregir(c1.id(), compra(item1, "5", "9", "F001", "124", doc2), ajeno));
		CajaChicaDto corregida = cajaChica.corregir(c1.id(), compra(item1, "5", "9", "F001", "124", doc2), solicitante);
		assertEquals("PENDIENTE_REGULARIZACION", corregida.estado());
		assertEquals(0, corregida.monto().compareTo(new BigDecimal("45")));

		// --- Logistica verifica: cuenta como atendido ---
		assertThrows(ApiException.class, () -> cajaChica.regularizar(c1.id(), null, solicitante));
		cajaChica.regularizar(c1.id(), "MOV-1", logistica);
		assertEquals("ATENCION_PARCIAL", estado(req.id()));

		CajaChicaDto c2 = cajaChica.registrar(req.id(), compra(item2, "3", "20", "B002", "7", comprobante()), logistica);
		cajaChica.regularizar(c2.id(), null, logistica);
		assertEquals("ATENCION_COMPLETA", estado(req.id()));
		assertFalse(cajaChica.formulario(req.id(), solicitante).puedeRegistrar());

		assertTrue(trackingService.obtener(req.id(), logistica).eventos().stream()
				.anyMatch(e -> e.accion().contains("caja chica del proyecto")));
		assertEquals(6, jdbc.queryForObject("select count(*) from log_cambios where entidad = 'CAJA_CHICA'", Integer.class),
				"registrar x2, observar, corregir, regularizar x2");
	}

	/**
	 * DEC-L054 (v0.39.0): de 5 unidades, 2 por caja chica y 3 por la via normal.
	 * Las 3 salen del stock; las 2 llegan a Necesidades sin apartar stock.
	 * Logistica divide esa necesidad y da a cada parte su ruta.
	 */
	@Test
	void unaParteDelProductoPorCajaChicaYElRestoNormal() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				select a.company_id, a.warehouse_id, v.product_id, v.variant_id, 'AJUSTE', 4, 'AJUSTE', gen_random_uuid(), ?
				from almacen a join empresa e on e.company_id = a.company_id and e.code = 'TDEMSRL'
				cross join producto_variante v where a.code = 'CENTRAL' and v.internal_variant_code in ('AC01003-00', 'AC01004-00')""",
				logistica.userId());

		ItemRequerimientoRequest guantes = item("AC01003-00", 5);
		ItemRequerimientoRequest otro = item("AC01004-00", 2);
		assertThrows(ApiException.class, () -> requerimientos.crear(new CrearRequerimientoRequest(cecoAdm(), "NORMAL",
				"CONSUMO", "Guantes para la obra del proyecto", LocalDate.now(LIMA).plusDays(5), List.of(
						new ItemRequerimientoRequest(guantes.variantId(), guantes.qtyRequested(), null, null, new BigDecimal("6"))),
				null, null), solicitante), "no mas de lo pedido");
		RequerimientoDto req = requerimientos.crear(new CrearRequerimientoRequest(cecoAdm(), "NORMAL", "CONSUMO",
				"Guantes para la obra del proyecto", LocalDate.now(LIMA).plusDays(5), List.of(
						new ItemRequerimientoRequest(guantes.variantId(), guantes.qtyRequested(), null, null, new BigDecimal("2")),
						otro),
				null, "Dos los compramos en la obra"), solicitante);
		assertTrue(req.cajaChicaSugerida());
		assertEquals(0, new BigDecimal("2").compareTo(requerimientos.obtener(req.id(), solicitante).items().stream()
				.filter(i -> "AC01003-00".equals(i.codigoVariante())).findFirst().orElseThrow().cantidadCajaChica()));
		requerimientos.enviar(req.id(), solicitante);
		aprobaciones.decidir(requerimientos.obtener(req.id(), aprobador).pendingApprovalId(),
				new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
		UUID itemGuantes = itemDe(req.id(), "AC01003-00");
		assertEquals(0, new BigDecimal("3").compareTo(reservado(itemGuantes)), "la parte normal sale del stock");
		assertEquals(0, new BigDecimal("2").compareTo(reservado(itemDe(req.id(), "AC01004-00"))), "lo no marcado, como siempre");

		List<NecesidadDto> deEste = abastecimiento.listarNecesidades(logistica).stream()
				.filter(x -> x.requestId().equals(req.id())).toList();
		assertEquals(1, deEste.size(), "solo la parte por caja chica llega a Necesidades");
		NecesidadDto n = deEste.get(0);
		assertEquals(0, n.qty().compareTo(new BigDecimal("2")));
		assertEquals("SOLICITANTE", n.suggestedReason());
		assertEquals("Dos los compramos en la obra", n.notaSolicitante());
		assertEquals(0, n.stockDisponible().compareTo(BigDecimal.ONE), "4 en stock - 3 reservadas");

		// --- Logistica divide: 1 a caja chica y 1 del almacen ---
		assertThrows(ApiException.class, () -> abastecimiento.dividir(n.needId(), new BigDecimal("2"), logistica));
		List<NecesidadDto> partes = abastecimiento.dividir(n.needId(), BigDecimal.ONE, logistica);
		assertEquals(2, partes.size());
		assertTrue(partes.stream().allMatch(x -> x.qty().compareTo(BigDecimal.ONE) == 0 && "SOLICITANTE".equals(x.suggestedReason())));
		abastecimiento.decidirRuta(partes.get(0).needId(), "CAJA_CHICA", logistica);
		abastecimiento.decidirRuta(partes.get(1).needId(), "STOCK", logistica);
		assertEquals(0, new BigDecimal("4").compareTo(reservado(itemGuantes)));
		assertEquals(0, BigDecimal.ONE.compareTo(cajaChica.formulario(req.id(), solicitante).items().get(0).pendiente()));
		ApiException sinStock = assertThrows(ApiException.class,
				() -> abastecimiento.decidirRuta(partes.get(0).needId(), "STOCK", logistica));
		assertTrue(sinStock.getMessage().contains("No hay stock"), sinStock.getMessage());
	}

	private BigDecimal reservado(UUID requestItemId) {
		return jdbc.queryForObject("""
				select coalesce(sum(qty_reserved), 0) from reserva_stock where request_item_id = ? and status = 'RESERVADA'""",
				BigDecimal.class, requestItemId);
	}

	@Test
	void reglaEditableSoloPorAdmin() {
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UsuarioPrincipal admin = principal("admin@logistica.test", "ADMIN");
		GuardarReglaRequest nueva = new GuardarReglaRequest(new BigDecimal("250"), true, true, false, true);
		assertThrows(ApiException.class, () -> cajaChica.guardarRegla(nueva, logistica));
		ReglaCajaChicaDto regla = cajaChica.guardarRegla(nueva, admin);
		assertEquals(0, regla.umbral().compareTo(new BigDecimal("250")));
		assertFalse(regla.fallaProveedor());
	}

	// ------------------------------------------------------------------

	private static RegistrarCajaChicaRequest compra(UUID requestItemId, String qty, String costo, String serie,
													String numero, UUID documentId) {
		return new RegistrarCajaChicaRequest(LocalDate.now(LIMA), "20555666777", "Ferreteria El Perno", "FACTURA",
				serie, numero, "PEN", documentId,
				List.of(new RegistrarCajaChicaRequest.Item(requestItemId, new BigDecimal(qty), new BigDecimal(costo))));
	}

	private UUID comprobante() {
		return jdbc.queryForObject("""
				insert into documento (document_type, file_name, file_url) values ('COMPROBANTE_CAJA_CHICA', 'boleta.jpg', 'x')
				returning document_id""", UUID.class);
	}

	private ItemRequerimientoRequest item(String variante, int qty) {
		UUID v = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?", UUID.class,
				variante);
		return new ItemRequerimientoRequest(v, BigDecimal.valueOf(qty), null, null);
	}

	private UUID itemDe(UUID req, String variante) {
		return jdbc.queryForObject("""
				select ri.request_item_id from requerimiento_item ri join producto_variante v on v.variant_id = ri.variant_id
				where ri.request_id = ? and v.internal_variant_code = ?""", UUID.class, req, variante);
	}

	private String estado(UUID req) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, req);
	}

	private UUID cecoAdm() {
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.code = 'ADM'""", UUID.class);
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class,
				correo), List.of(rol));
	}
}
