package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.documentos.DocumentosService;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RecepcionOrdenDto;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.OrdenCompraService;
import com.tdem.logistica.modules.compras.OrdenDirectaService;
import com.tdem.logistica.modules.compras.dto.CrearOrdenDirectaRequest;
import com.tdem.logistica.modules.compras.dto.CrearOrdenDirectaRequest.Linea;
import com.tdem.logistica.modules.compras.dto.LiberarOrdenRequest;
import com.tdem.logistica.modules.compras.dto.OrdenDto;
import com.tdem.logistica.modules.compras.dto.RegistrarPagoRequest;
import com.tdem.logistica.security.UsuarioPrincipal;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DEC-L080: OC directa sin cotizacion. Sale aprobada; los productos se liberan
 * y reciben como siempre, los servicios nunca pasan por el almacen y una orden
 * solo de servicios se cierra al pagarse.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class OrdenDirectaTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private OrdenDirectaService directas;
	@Autowired private OrdenCompraService ordenCompra;
	@Autowired private ComprasService compras;
	@Autowired private RecepcionService recepcion;
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
	void productoYServicioSaleAprobadaYSoloElProductoPasaPorElAlmacen() throws IOException {
		UsuarioPrincipal log = logistica();
		UUID prov = proveedor("20100000081");
		String req = requerimiento();
		OrdenDto oc = directas.crear(request(prov, "TDEMSRL", cecoTdem(), req, List.of(
				new Linea(variante("AC01006-00"), null, new BigDecimal("3"), new BigDecimal("10")),
				new Linea(null, "Instalación del equipo en obra", BigDecimal.ONE, new BigDecimal("70")))), log);

		assertEquals("OC_DIRECTA", oc.orderType());
		assertEquals("EMITIDA", oc.status(), "sale aprobada, sin pasar por Gerencia");
		assertNotNull(oc.approvedAt());
		assertFalse(oc.servicesOnly());
		assertEquals("ADM", oc.cecoCode());
		assertEquals(req, oc.requestCode(), "el requerimiento de referencia se ve en la orden");
		assertEquals(0, oc.subtotal().compareTo(new BigDecimal("100.00")));
		assertEquals(0, oc.total().compareTo(new BigDecimal("118.00")));
		assertTrue(compras.listarOrdenes(log).stream().anyMatch(o -> o.orderId().equals(oc.orderId())),
				"aparece en la lista aunque no tenga cotizacion");
		assertEquals(1, jdbc.queryForObject("select count(*) from proveedor_producto where supplier_id = ?",
				Integer.class, prov), "el proveedor queda habilitado para el producto");

		// El almacen solo ve el producto; recibido completo, la orden queda completa.
		ordenCompra.liberar(oc.orderId(), new LiberarOrdenRequest(null), log);
		RecepcionOrdenDto ro = recepcion.obtenerRecepcionOrden(oc.orderId(), log);
		assertEquals(1, ro.items().size(), "el servicio no se recibe en almacen");
		RecepcionOrdenDto despues = recepcion.registrar(oc.orderId(), new RegistrarRecepcionRequest(List.of(
				new RecepcionItemInput(ro.items().get(0).orderItemId(), new BigDecimal("3"), "CONFORME", null, null)),
				null, null), log);
		assertEquals("RECEPCION_COMPLETA", despues.orden().status());

		// PDF F-LOG-09 aprobado, con la linea de servicio.
		String texto;
		try (var doc = Loader.loadPDF(documentos.orden(oc.orderId(), log).contenido())) {
			texto = new PDFTextStripper().getText(doc);
		}
		assertTrue(texto.contains("Instalación del equipo en obra") && texto.contains("SERV"), texto);
		assertFalse(texto.contains("PENDIENTE DE APROBACIÓN"));
	}

	@Test
	void soloServiciosNoSeLiberaYSeCierraAlPagar() {
		UsuarioPrincipal log = logistica();
		OrdenDto oc = directas.crear(request(proveedor("20100000082"), "TDEMSRL", null, null, List.of(
				new Linea(null, "Mantenimiento preventivo de camioneta", BigDecimal.ONE, new BigDecimal("450")))), log);
		assertTrue(oc.servicesOnly());
		assertNull(oc.bloqueoRecepcion());
		assertThrows(ApiException.class, () -> ordenCompra.liberar(oc.orderId(), new LiberarOrdenRequest(null), log));
		assertTrue(recepcion.listarPorRecibir(log).stream().noneMatch(o -> o.orderId().equals(oc.orderId())));

		OrdenDto anticipo = ordenCompra.registrarPago(oc.orderId(),
				new RegistrarPagoRequest("ANTICIPO", constancia()), log);
		assertEquals("EMITIDA", anticipo.status(), "con anticipo sigue abierta");
		OrdenDto pagada = ordenCompra.registrarPago(oc.orderId(), new RegistrarPagoRequest("PAGADO", constancia()), log);
		assertEquals("CERRADA", pagada.status());
	}

	@Test
	void validaciones() {
		UsuarioPrincipal log = logistica();
		UUID prov = proveedor("20100000083");
		Linea servicio = new Linea(null, "Servicio", BigDecimal.ONE, BigDecimal.TEN);
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "TDEMSRL", null, null, List.of(servicio)),
				new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"))));
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "TDEMSRL", null, null,
				List.of(new Linea(null, " ", BigDecimal.ONE, BigDecimal.TEN))), log), "servicio sin descripcion");
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "METRIN", cecoTdem(), null,
				List.of(servicio)), log), "CECO de otra empresa");
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "TDEMSRL", null, "REQ-NO-EXISTE",
				List.of(servicio)), log));
		UUID v = variante("AC01007-00");
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "TDEMSRL", null, null, List.of(
				new Linea(v, null, BigDecimal.ONE, BigDecimal.TEN), new Linea(v, null, BigDecimal.ONE, BigDecimal.TEN))),
				log), "producto repetido");
		jdbc.update("update proveedor set active = false where supplier_id = ?", prov);
		assertThrows(ApiException.class, () -> directas.crear(request(prov, "TDEMSRL", null, null, List.of(servicio)),
				log), "proveedor no habilitado");
	}

	// ------------------------------------------------------------------

	private static CrearOrdenDirectaRequest request(UUID prov, String empresa, UUID ceco, String req, List<Linea> lineas) {
		return new CrearOrdenDirectaRequest(empresa, prov, "PEN", "CONTADO", "ALMACEN_CLIENTE", "Almacén central",
				"3 días", "MAS_IGV", null, ceco, req, lineas, null);
	}

	private UUID proveedor(String ruc) {
		return jdbc.queryForObject("insert into proveedor (ruc, legal_name) values (?, ?) returning supplier_id",
				UUID.class, ruc, "Proveedor " + ruc);
	}

	private UUID variante(String codigo) {
		return jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?",
				UUID.class, codigo);
	}

	private UUID cecoTdem() {
		return jdbc.queryForObject("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				 where e.code = 'TDEMSRL' and c.code = 'ADM'""", UUID.class);
	}

	private String requerimiento() {
		String codigo = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
		jdbc.update("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				select ?, e.company_id, c.ceco_id, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'APROVISIONAMIENTO'
				  from empresa e join ceco c on c.company_id = e.company_id and c.code = 'ADM' where e.code = 'TDEMSRL'""",
				codigo, usuario("solicitante@logistica.test"));
		return codigo;
	}

	private UUID constancia() {
		return jdbc.queryForObject("insert into documento (document_type, file_name, file_url) "
				+ "values ('CONSTANCIA_PAGO', 'pago.pdf', 'https://x/pago.pdf') returning document_id", UUID.class);
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
