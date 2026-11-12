package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComparativoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.CotizacionOcrParser;
import com.tdem.logistica.modules.proveedores.EquivalenciaService;
import com.tdem.logistica.modules.proveedores.dto.EquivalenciaDto;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.OcrExtractor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DEC-L047: comparativo de hasta tres cotizaciones por grupo de consolidados.
 * La lectura OCR propone proveedor (por RUC) y precios por nombre de producto;
 * guardar crea la RFQ del proveedor si no existia; se adjudica todo de una vez.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ComparativoCotizacionesTest {

	private static final String COTIZACION = """
			FERRETERIA LOS ANDES S.A.C.
			RUC 20100000021
			ITEM DESCRIPCION CANT P.UNIT TOTAL
			1 Camara endoscopio HD 3m luz LED wifi 10 85.50 855.00
			2 Repulsor ultrasonico canes 4 32.00 128.00
			TOTAL S/ 983.00
			Plazo de entrega 5 dias
			""";

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComparativoService comparativo;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private ComprasService compras;
	@Autowired private EquivalenciaService equivalencias;
	@MockitoBean private OcrExtractor ocr;

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
	void tresCotizacionesLeidasComparadasYAdjudicadas() {
		UsuarioPrincipal logistica = logistica();
		UUID s1 = proveedor("20100000011");
		UUID s2 = proveedor("20100000012");
		UUID s3 = proveedor("20100000021");
		UUID s4 = proveedor("20100000013");
		abastecimiento.reemplazarCobertura(producto("AC01003"), List.of(s1), logistica);

		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01003-00", 10);
		pedido.put("AC01008-00", 4);
		aprobar(requerimiento(pedido));
		UUID cx = consolidado("AC01003-00");
		UUID cy = consolidado("AC01008-00");
		List<UUID> ids = List.of(cx, cy);

		ComparativoDto vacio = comparativo.obtener(ids, logistica);
		assertEquals("TDEMSRL", vacio.empresaCode());
		assertEquals(2, vacio.filas().size());
		assertTrue(vacio.proveedores().isEmpty(), "aun no hay RFQ");

		// --- Lectura: proveedor por RUC y precio unitario por nombre de producto ---
		when(ocr.extraerTexto(any(), anyString())).thenReturn(COTIZACION);
		OcrComparativoDto leida = comparativo.leer(ids, archivo(), null, logistica);
		assertEquals(s3, leida.supplierId());
		assertEquals(0, new BigDecimal("85.50").compareTo(leida.precios().get(cx)));
		assertEquals(0, new BigDecimal("32.00").compareTo(leida.precios().get(cy)));
		assertEquals(5, leida.plazoDetectadoDias());

		// --- Tres cotizaciones; la de s3 crea su RFQ y lo habilita para esos productos ---
		ComparativoDto c = registrar(ids, s3, null, true, Map.of(cx, "85.50", cy, "32.00"));
		ComparativoDto.Columna col3 = columna(c, s3);
		assertEquals("OCR", col3.source());
		assertEquals(2, col3.precios().size());
		assertEquals("RESPONDIDA", jdbc.queryForObject("select status from rfq where rfq_id = ?", String.class, col3.rfqId()));
		assertEquals(2, jdbc.queryForObject("select count(*) from proveedor_producto where supplier_id = ?", Integer.class, s3));
		assertEquals("EN_COTIZACION", estado(cx));

		registrar(ids, s1, null, false, Map.of(cx, "80.00"));
		c = registrar(ids, s2, null, false, Map.of(cx, "90.00", cy, "30.00"));
		assertEquals(3, c.proveedores().stream().filter(x -> x.quotationId() != null).count());

		ApiException cuarta = assertThrows(ApiException.class, () -> registrar(ids, s4, null, false, Map.of(cx, "70.00")));
		assertTrue(cuarta.getMessage().contains("ya tiene 3 cotizaciones"), cuarta.getMessage());
		ApiException repetida = assertThrows(ApiException.class, () -> registrar(ids, s1, null, false, Map.of(cx, "79.00")));
		assertTrue(repetida.getMessage().contains("Reemplazar"), repetida.getMessage());

		// --- Reemplazo: la nueva queda vigente y la anterior se descarta ---
		UUID anterior = columna(c, s1).quotationId();
		c = registrar(ids, s1, anterior, false, Map.of(cx, "79.00"));
		assertEquals(0, new BigDecimal("79.00").compareTo(columna(c, s1).precios().get(0).unitCost()));
		assertEquals("DESCARTADA", jdbc.queryForObject("select status from cotizacion where quotation_id = ?", String.class, anterior));
		assertEquals(3, c.proveedores().stream().filter(x -> x.quotationId() != null).count());

		// --- Adjudicar el mejor precio de cada producto de una vez ---
		UUID mejorX = precio(columna(c, s1), cx).quotationItemId();
		UUID mejorY = precio(columna(c, s2), cy).quotationItemId();
		List<AdjudicarComparativoRequest.Eleccion> elecciones = List.of(
				new AdjudicarComparativoRequest.Eleccion(cx, mejorX), new AdjudicarComparativoRequest.Eleccion(cy, mejorY));
		ComparativoDto adj = comparativo.adjudicar(new AdjudicarComparativoRequest(ids, elecciones), logistica);
		assertEquals(mejorX, fila(adj, cx).adjudicadoQuotationItemId());
		assertEquals(mejorY, fila(adj, cy).adjudicadoQuotationItemId());
		assertEquals("ADJUDICADA", estado(cy));
		assertThrows(ApiException.class, () -> comparativo.adjudicar(new AdjudicarComparativoRequest(ids, elecciones), logistica));
		assertEquals(2, abastecimiento.listarPorEmitir(logistica).size(), "una OC por proveedor ganador");

		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> comparativo.obtener(ids, solicitante));
	}

	/**
	 * DEC-L053: el proveedor llama distinto a nuestros productos. La primera vez
	 * Logistica elige la linea; se guarda como equivalencia y la siguiente lectura
	 * de ese proveedor la reconoce sola. La OC muestra su referencia.
	 */
	@Test
	void aprendeComoLlamaElProveedorACadaProducto() {
		UsuarioPrincipal logistica = logistica();
		UUID s = proveedor("20100000051");
		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01004-00", 6);
		pedido.put("AC01005-00", 2);
		aprobar(requerimiento(pedido));
		UUID ca = consolidado("AC01004-00");
		UUID cb = consolidado("AC01005-00");
		List<UUID> ids = List.of(ca, cb);
		String documento = """
				DISTRIBUIDORA DEL SUR S.A.C.
				RUC 20100000051
				1 ZX-900 Articulo generico modelo zeta 6 15.00 90.00
				2 Repuesto marca omega gris 2 40.00 80.00
				TOTAL S/ 170.00
				""";
		when(ocr.extraerTexto(any(), anyString())).thenReturn(documento);

		// --- Primera vez: nada en comun con nuestros nombres, no adivina ---
		OcrComparativoDto primera = comparativo.leer(ids, archivo(), null, logistica);
		assertEquals(s, primera.supplierId(), "por su RUC");
		assertTrue(primera.precios().isEmpty());
		assertEquals(2, primera.lineas().size());
		OcrLineaDto la = primera.lineas().get(0);
		OcrLineaDto lb = primera.lineas().get(1);
		assertEquals("ZX-900", la.codigo());

		// --- Logistica elige la linea de cada producto y guarda ---
		comparativo.registrar(new RegistrarComparativoRequest(s, ids, null, null, null, "PEN", "CONTADO", "AGENCIA",
				3, null, null, true, List.of(
						new RegistrarComparativoRequest.Precio(ca, new BigDecimal("15.00"), null, null, null,
								la.descripcion(), la.codigo()),
						new RegistrarComparativoRequest.Precio(cb, new BigDecimal("40.00"), null, null, null,
								lb.descripcion(), lb.codigo()))), logistica);
		assertEquals(2, jdbc.queryForObject("select count(*) from proveedor_equivalencia where supplier_id = ?",
				Integer.class, s));
		assertEquals("ZX-900", jdbc.queryForObject("""
				select ci.supplier_code from cotizacion_item ci join cotizacion c on c.quotation_id = ci.quotation_id
				where c.supplier_id = ? and ci.unit_cost = 15.00""", String.class, s));

		// --- Segunda lectura del mismo proveedor: la reconoce sola ---
		OcrComparativoDto segunda = comparativo.leer(ids, archivo(), s, logistica);
		assertEquals(0, new BigDecimal("15.00").compareTo(segunda.precios().get(ca)));
		assertEquals(0, new BigDecimal("40.00").compareTo(segunda.precios().get(cb)));
		assertEquals("EQUIVALENCIA", segunda.coincidencias().get(ca).origen());
		assertEquals(lb.indice(), segunda.coincidencias().get(cb).indiceLinea());

		// --- Reconfirmar una descripcion con otro producto: gana la ultima y se audita ---
		ComparativoDto c = comparativo.obtener(ids, logistica);
		UUID vigente = c.proveedores().stream().filter(x -> s.equals(x.supplierId())).findFirst().orElseThrow().quotationId();
		comparativo.registrar(new RegistrarComparativoRequest(s, ids, null, null, null, "PEN", "CONTADO", "AGENCIA",
				3, null, vigente, true, List.of(
						new RegistrarComparativoRequest.Precio(ca, new BigDecimal("15.00"), null, null, null,
								lb.descripcion(), null))), logistica);
		assertEquals(producto("AC01004"), jdbc.queryForObject("""
				select product_id from proveedor_equivalencia where supplier_id = ? and description_key = ?""",
				UUID.class, s, CotizacionOcrParser.clave(lb.descripcion())));
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'PROVEEDOR' and accion = 'CAMBIAR_EQUIVALENCIA'
				and entidad_id = ?""", Integer.class, s));

		// --- La OC lleva la referencia del proveedor ---
		c = comparativo.obtener(ids, logistica);
		ComparativoDto.Columna col = c.proveedores().stream().filter(x -> s.equals(x.supplierId())).findFirst().orElseThrow();
		comparativo.adjudicar(new AdjudicarComparativoRequest(List.of(ca), List.of(
				new AdjudicarComparativoRequest.Eleccion(ca, precio(col, ca).quotationItemId()))), logistica);
		OrdenDto oc = abastecimiento.emitirOrden(col.quotationId(), logistica);
		OrdenDetalleDto detalle = compras.obtenerOrden(oc.orderId(), logistica);
		assertEquals(lb.descripcion(), detalle.items().get(0).referenciaProveedor());

		// --- Quitar una equivalencia desde la ficha del proveedor ---
		List<EquivalenciaDto> suyas = equivalencias.listar(s, logistica);
		assertEquals(2, suyas.size());
		equivalencias.quitar(suyas.get(0).equivalenceId(), logistica);
		assertEquals(1, equivalencias.listar(s, logistica).size());
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> equivalencias.listar(s, solicitante));
	}

	@Test
	void elListadoDeRequerimientosResumeSusProductos() {
		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01009-00", 1);
		pedido.put("AC01002-00", 2);
		UUID aprobacion = requerimiento(pedido);
		UUID reqId = jdbc.queryForObject("select entity_id from aprobacion where approval_id = ?", UUID.class, aprobacion);
		RequerimientoDto r = requerimientos.listar(logistica()).stream()
				.filter(x -> x.id().equals(reqId)).findFirst().orElseThrow();
		assertEquals(2, r.itemsCount());
		assertTrue(r.resumenItems().contains(" · "), r.resumenItems());
	}

	// ------------------------------------------------------------------

	private ComparativoDto registrar(List<UUID> ids, UUID proveedor, UUID reemplaza, boolean ocr, Map<UUID, String> precios) {
		List<RegistrarComparativoRequest.Precio> items = precios.entrySet().stream()
				.map(e -> new RegistrarComparativoRequest.Precio(e.getKey(), new BigDecimal(e.getValue()), null, null, null, null, null))
				.toList();
		return comparativo.registrar(new RegistrarComparativoRequest(proveedor, ids, null, null, null, "PEN",
				"CONTADO", "AGENCIA", 5, null, reemplaza, ocr, items), logistica());
	}

	private static ComparativoDto.Columna columna(ComparativoDto c, UUID proveedor) {
		return c.proveedores().stream().filter(x -> x.supplierId().equals(proveedor)).findFirst().orElseThrow();
	}

	private static ComparativoDto.Precio precio(ComparativoDto.Columna c, UUID consolidado) {
		return c.precios().stream().filter(p -> p.consolidationId().equals(consolidado)).findFirst().orElseThrow();
	}

	private static ComparativoDto.Fila fila(ComparativoDto c, UUID consolidado) {
		return c.filas().stream().filter(f -> f.consolidationId().equals(consolidado)).findFirst().orElseThrow();
	}

	private static MockMultipartFile archivo() {
		return new MockMultipartFile("file", "cotizacion.pdf", "application/pdf", new byte[] {1, 2, 3});
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

	private String estado(UUID consolidationId) {
		return jdbc.queryForObject("select status from consolidacion where consolidation_id = ?", String.class, consolidationId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
