package com.tdem.logistica;

import tools.jackson.databind.ObjectMapper;
import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.config.LimiteSolicitudes;
import com.tdem.logistica.documentos.DocumentosService;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComparativoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.EnlaceCotizacionService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-L052: el proveedor cotiza desde un enlace unico, sin cuenta, sobre las
 * lineas de su RFQ. El token solo se guarda como hash, vence, se anula y no
 * deja ver nada interno; la cotizacion entra al comparativo como ENLACE.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class EnlaceCotizacionTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComparativoService comparativo;
	@Autowired private ComprasService compras;
	@Autowired private EnlaceCotizacionService enlaces;
	@Autowired private DocumentosService documentos;
	@Autowired private LimiteSolicitudes limite;
	@Autowired private WebApplicationContext contexto;
	@Autowired private ObjectMapper json;
	@MockitoBean private StorageClient storage;

	@DynamicPropertySource
	static void bd(DynamicPropertyRegistry registry) throws IOException, SQLException {
		pg = BdRrhhSimulada.iniciar();
		registry.add("spring.datasource.url", () -> BdRrhhSimulada.jdbcUrl(pg));
		registry.add("spring.datasource.username", () -> BdRrhhSimulada.USUARIO_APP);
		registry.add("spring.datasource.password", () -> BdRrhhSimulada.PASSWORD_APP);
		registry.add("app.jwt.secret", () -> "clave-de-prueba-solo-para-tests-0123456789-abcdefghijklmnop");
		registry.add("app.public-url", () -> "https://logistica.ejemplo.pe/");
		registry.add("app.publico.limite-por-minuto", () -> "5");
	}

	@AfterAll
	static void detener() throws IOException {
		if (pg != null) {
			pg.close();
		}
	}

	@Test
	void elProveedorCotizaDesdeSuEnlace() throws Exception {
		UsuarioPrincipal logistica = logistica();
		UUID proveedor = proveedor("20100000031");
		abastecimiento.reemplazarCobertura(producto("AC01003"), List.of(proveedor), logistica);
		abastecimiento.reemplazarCobertura(producto("AC01008"), List.of(proveedor), logistica);
		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01003-00", 10);
		pedido.put("AC01008-00", 4);
		UUID aprobacion = requerimiento(pedido);
		String requestCode = jdbc.queryForObject("""
				select r.request_code from aprobacion a join requerimiento r on r.request_id = a.entity_id
				where a.approval_id = ?""", String.class, aprobacion);
		aprobar(aprobacion);
		UUID cx = consolidado("AC01003-00");
		UUID cy = consolidado("AC01008-00");
		RfqDto rfq = abastecimiento.prepararSolicitudes(List.of(cx, cy), logistica).stream()
				.filter(r -> r.supplierId().equals(proveedor)).findFirst().orElseThrow();

		// --- Solo Logistica genera; se guarda el hash, no el token ---
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> enlaces.generar(rfq.rfqId(), solicitante));
		EnlaceCotizacionDto enlace = enlaces.generar(rfq.rfqId(), logistica);
		String token = enlace.token();
		assertEquals("https://logistica.ejemplo.pe/cotizar/" + token, enlace.url());
		assertTrue(token.length() >= 40, "token de 32 bytes");
		String guardado = jdbc.queryForObject("select token_hash from rfq_enlace where rfq_id = ?", String.class, rfq.rfqId());
		assertNotEquals(token, guardado);
		assertEquals(64, guardado.length());
		assertNull(enlaces.vigente(rfq.rfqId(), logistica).url(), "despues no se puede recuperar el enlace");

		// --- El PDF de la RFQ lleva el enlace y el QR ---
		String pdf = texto(documentos.rfq(rfq.rfqId(), token, logistica).contenido());
		assertTrue(pdf.contains("Cotice en línea"), pdf);
		assertTrue(pdf.replaceAll("\\s", "").contains(token), "la URL completa va impresa");

		// --- El proveedor ve solo la solicitud, nada interno ---
		CotizacionPublicaDto vista = enlaces.obtener(token);
		assertEquals(2, vista.lineas().size());
		assertEquals(rfq.rfqCode(), vista.rfqCode());
		assertNull(vista.enviada());
		String expuesto = json.writeValueAsString(vista);
		assertFalse(expuesto.contains(requestCode), "no muestra el requerimiento");
		assertFalse(expuesto.contains("ADMINISTRACION"), "no muestra el CECO");
		assertFalse(expuesto.toLowerCase().contains("costo"), "no muestra costos");
		assertNotNull(jdbc.queryForObject("select opened_at from rfq_enlace where rfq_id = ?", java.sql.Timestamp.class, rfq.rfqId()));

		// --- Cada linea dice su unidad por nombre; ND queda sin nombre ("por definir") ---
		jdbc.update("""
				update rfq_item set uom_id = (select uom_id from unidad_medida where code = 'MILLAR')
				where rfq_item_id = ?""", linea(vista, "AC01003-00"));
		CotizacionPublicaDto conUnidad = enlaces.obtener(token);
		assertEquals("Millar", conUnidad.lineas().stream().filter(l -> l.sku().equals("AC01003-00"))
				.findFirst().orElseThrow().unidadNombre());
		conUnidad.lineas().stream().filter(l -> "ND".equals(l.unidad()))
				.forEach(l -> assertNull(l.unidadNombre(), "una unidad no definida no se muestra como tal"));

		// --- Envia: entra al comparativo como ENLACE y la RFQ queda respondida ---
		UUID lx = linea(vista, "AC01003-00");
		UUID ly = linea(vista, "AC01008-00");
		org.mockito.Mockito.when(storage.subir(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString())).thenReturn("https://archivos.ejemplo.pe/cot.pdf");
		MockMultipartFile adjunto = new MockMultipartFile("archivo", "cot.pdf", "application/pdf", new byte[] {1, 2, 3});
		CotizacionPublicaDto enviada = enlaces.enviar(token, pedido(Map.of(lx, "84.00", ly, "31.00")), adjunto);
		assertNotNull(enviada.enviada());
		assertEquals("cot.pdf", enviada.enviada().documentoNombre());
		assertEquals("RESPONDIDA", jdbc.queryForObject("select status from rfq where rfq_id = ?", String.class, rfq.rfqId()));
		ComparativoDto c = comparativo.obtener(List.of(cx, cy), logistica);
		ComparativoDto.Columna col = c.proveedores().stream()
				.filter(x -> x.supplierId().equals(proveedor)).findFirst().orElseThrow();
		assertEquals("ENLACE", col.source());
		assertEquals(2, col.precios().size());

		// --- Reenviar reemplaza la anterior ---
		UUID primera = col.quotationId();
		CotizacionPublicaDto corregida = enlaces.enviar(token, pedido(Map.of(lx, "82.50")), null);
		assertEquals(1, corregida.enviada().precios().size());
		assertEquals(0, new BigDecimal("82.50").compareTo(corregida.enviada().precios().get(0).unitCost()));
		assertEquals("DESCARTADA", jdbc.queryForObject("select status from cotizacion where quotation_id = ?", String.class, primera));
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from cotizacion where rfq_id = ? and source = 'ENLACE' and status <> 'DESCARTADA'""",
				Integer.class, rfq.rfqId()));

		// --- Precio de una linea ajena o condicion invalida ---
		assertThrows(ApiException.class, () -> enlaces.enviar(token, pedido(Map.of(UUID.randomUUID(), "10.00")), null));
		assertThrows(ApiException.class, () -> enlaces.enviar(token, new EnviarCotizacionEnlaceRequest("PEN", "TRUEQUE",
				"AGENCIA", 3, null, null, List.of(new EnviarCotizacionEnlaceRequest.Precio(lx, BigDecimal.TEN, null, null))), null));

		// --- Anular, regenerar, vencer, token inventado ---
		enlaces.anular(rfq.rfqId(), logistica);
		assertNoDisponible(token);
		String nuevo = enlaces.generar(rfq.rfqId(), logistica).token();
		assertNoDisponible(token);
		assertNotNull(enlaces.obtener(nuevo));
		assertThrows(ApiException.class, () -> documentos.rfq(rfq.rfqId(), token, logistica), "el PDF no acepta un enlace viejo");
		assertNoDisponible("x".repeat(43));
		jdbc.update("update rfq_enlace set expires_at = now() - interval '1 minute' where revoked_at is null and rfq_id = ?", rfq.rfqId());
		assertNoDisponible(nuevo);

		// --- RFQ cerrada: ni el enlace vigente funciona ni se genera otro ---
		String ultimo = enlaces.generar(rfq.rfqId(), logistica).token();
		compras.cerrarRfq(rfq.rfqId(), "prueba de cierre", logistica);
		assertNoDisponible(ultimo);
		assertThrows(ApiException.class, () -> enlaces.generar(rfq.rfqId(), logistica));
	}

	@Test
	void laRutaPublicaNoPideSesionYTieneTopePorIp() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(contexto).addFilters(limite).apply(springSecurity()).build();
		String ip = "203.0.113.7";
		for (int i = 0; i < 5; i++) {
			// Sin sesion: un token invalido responde 404 (no 401) y no dice si existio.
			mvc.perform(get("/api/publico/cotizacion/" + "y".repeat(43)).header("X-Forwarded-For", "1.1.1.1, " + ip))
					.andExpect(status().isNotFound());
		}
		mvc.perform(get("/api/publico/cotizacion/" + "y".repeat(43)).header("X-Forwarded-For", "9.9.9.9, " + ip))
				.andExpect(status().isTooManyRequests());
		// La IP que escribe el cliente al inicio no evita el tope; otra IP real si pasa.
		mvc.perform(get("/api/publico/cotizacion/" + "y".repeat(43)).header("X-Forwarded-For", "203.0.113.8"))
				.andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------

	private void assertNoDisponible(String token) {
		ApiException e = assertThrows(ApiException.class, () -> enlaces.obtener(token));
		assertTrue(e.getMessage().contains("no es válido"), e.getMessage());
	}

	private static EnviarCotizacionEnlaceRequest pedido(Map<UUID, String> precios) {
		List<EnviarCotizacionEnlaceRequest.Precio> items = precios.entrySet().stream()
				.map(e -> new EnviarCotizacionEnlaceRequest.Precio(e.getKey(), new BigDecimal(e.getValue()), "MARCA", null))
				.toList();
		return new EnviarCotizacionEnlaceRequest("PEN", "CONTADO", "AGENCIA", 5, null, "COT-77", items);
	}

	private static UUID linea(CotizacionPublicaDto v, String sku) {
		return v.lineas().stream().filter(l -> sku.equals(l.sku())).findFirst().orElseThrow().rfqItemId();
	}

	private static String texto(byte[] pdf) throws IOException {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private void aprobar(UUID approvalId) {
		UsuarioPrincipal aprobador = new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"));
		aprobacionService.decidir(approvalId, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
	}

	private UUID consolidado(String codigoVariante) {
		return abastecimiento.listarConsolidados(logistica()).stream()
				.filter(c -> codigoVariante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow().consolidationId();
	}

	/** Requerimiento de TDEM pendiente de aprobacion; devuelve su aprobacion. */
	private UUID requerimiento(Map<String, Integer> pedido) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', 'NORMAL') returning request_id""",
				UUID.class, "REQ-" + UUID.randomUUID().toString().substring(0, 8), empresa, cecoId,
				usuario("solicitante@logistica.test"));
		pedido.forEach((variante, qty) -> jdbc.update("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ?""", reqId, qty, variante));
		return jdbc.queryForObject("insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id",
				UUID.class, reqId);
	}

	private UUID proveedor(String ruc) {
		return jdbc.queryForObject("insert into proveedor (ruc, legal_name) values (?, ?) returning supplier_id",
				UUID.class, ruc, "Proveedor " + ruc);
	}

	private UUID producto(String skuBase) {
		return jdbc.queryForObject("select product_id from producto where sku_base = ?", UUID.class, skuBase);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
