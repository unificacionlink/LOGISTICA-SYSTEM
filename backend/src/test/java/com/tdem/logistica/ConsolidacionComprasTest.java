package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.RecepcionService;
import com.tdem.logistica.modules.almacen.dto.RecepcionItemInput;
import com.tdem.logistica.modules.almacen.dto.RegistrarRecepcionRequest;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.cajachica.CajaChicaService;
import com.tdem.logistica.modules.cajachica.dto.EvaluacionCajaChicaDto;
import com.tdem.logistica.modules.compras.AbastecimientoService;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.compras.OrdenCompraService;
import com.tdem.logistica.modules.compras.dto.DecidirOrdenRequest;
import com.tdem.logistica.modules.compras.dto.LiberarOrdenRequest;
import com.tdem.logistica.documentos.DocumentosService;
import com.tdem.logistica.modules.identidad.EmpresaAdminService;
import com.tdem.logistica.modules.identidad.dto.EditarEmpresaRequest;
import com.tdem.logistica.modules.identidad.dto.EmpresaDatosDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lote 16 (DEC-L039): al aprobar, el faltante crea una necesidad que entra al
 * consolidado de su SKU (entre requerimientos y CECOs); Logistica prepara RFQ
 * multiproducto por proveedor, adjudica por producto y emite la OC por
 * cotizacion; lo recibido se reparte entre los requerimientos de origen.
 * AprobacionService.decidir real sobre Postgres (BdRrhhSimulada). Cada test usa
 * SKUs distintos.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ConsolidacionComprasTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private OrdenCompraService ordenCompra;
	@Autowired private ComprasService compras;
	@Autowired private RecepcionService recepcion;
	@Autowired private CajaChicaService cajaChica;
	@Autowired private DocumentosService documentos;
	@Autowired private EmpresaAdminService empresas;

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
	void stockParcialCreaNecesidadQueEntraAlConsolidado() {
		stock("MU03004-00", 2);
		Req req = requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("MU03004-00", 5));

		aprobar(req);

		UUID item = req.items.get("MU03004-00");
		assertEquals("EN_ATENCION", estado(req));
		assertEquals(0, reservado(item).compareTo(new BigDecimal("2")));
		Map<String, Object> n = jdbc.queryForMap("""
				select n.status, n.qty, c.status as consolidado, c.total_qty from necesidad_compra n
				join consolidacion c on c.consolidation_id = n.consolidation_id where n.request_item_id = ?""", item);
		assertEquals("COMPRA", n.get("status"));
		assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) n.get("qty")));
		assertEquals("ABIERTA", n.get("consolidado"));

		ConsolidadoDto c = consolidado("MU03004-00");
		assertTrue(c.proveedores().isEmpty());
		ApiException sin = assertThrows(ApiException.class,
				() -> abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica()));
		assertTrue(sin.getMessage().contains("Sin proveedor habilitado: MU03004"), sin.getMessage());
	}

	@Test
	void conStockSuficienteNoHayNecesidad() {
		stock("MU03006-00", 10);
		Req req = requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("MU03006-00", 4));

		aprobar(req);

		assertEquals(0, reservado(req.items.get("MU03006-00")).compareTo(new BigDecimal("4")));
		assertEquals(0, jdbc.queryForObject("select count(*) from necesidad_compra where request_item_id = ?",
				Integer.class, req.items.get("MU03006-00")));
	}

	@Test
	void consolidaEntreRequerimientosCotizaPorProveedorAdjudicaYReparte() {
		UsuarioPrincipal logistica = logistica();
		UUID s1 = proveedor("20100000011");
		UUID s2 = proveedor("20100000012");
		abastecimiento.reemplazarCobertura(producto("AC01001"), List.of(s1, s2), logistica);
		abastecimiento.reemplazarCobertura(producto("AC01002"), List.of(s1), logistica);

		Map<String, Integer> pedidoA = new LinkedHashMap<>();
		pedidoA.put("AC01001-00", 3);
		pedidoA.put("AC01002-00", 2);
		Req a = requerimiento("ADM", "APROVISIONAMIENTO", null, pedidoA);
		Req b = requerimiento("OPS", "URGENTE", LocalDate.now(LIMA).plusDays(10), Map.of("AC01001-00", 4));
		aprobar(a);
		aprobar(b);

		// --- Un consolidado por SKU, con el REQ y el CECO de cada parte ---
		ConsolidadoDto cx = consolidado("AC01001-00");
		ConsolidadoDto cy = consolidado("AC01002-00");
		assertEquals(0, cx.totalQty().compareTo(new BigDecimal("7")));
		assertEquals(List.of("ADM", "OPS"), cx.origenes().stream().map(OrigenDto::cecoCode).sorted().toList());
		assertEquals(2, cx.proveedores().size());

		// --- RFQ multiproducto: una por proveedor ---
		List<RfqDto> rfqs = abastecimiento.prepararSolicitudes(List.of(cx.consolidationId(), cy.consolidationId()), logistica);
		assertEquals(2, rfqs.size());
		RfqDto r1 = rfqs.stream().filter(r -> r.supplierId().equals(s1)).findFirst().orElseThrow();
		RfqDto r2 = rfqs.stream().filter(r -> r.supplierId().equals(s2)).findFirst().orElseThrow();
		assertEquals("BORRADOR", r1.status());
		assertNull(r1.requestId(), "cotiza dos requerimientos");
		assertEquals(2, compras.obtenerRfq(r1.rfqId(), logistica).items().size());
		assertEquals(1, compras.obtenerRfq(r2.rfqId(), logistica).items().size());
		assertThrows(ApiException.class, () -> abastecimiento.prepararSolicitudes(List.of(cx.consolidationId()), logistica),
				"un consolidado se cotiza una sola vez");

		// --- Lo aprobado despues abre otro consolidado ---
		Req c = requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("AC01001-00", 1));
		aprobar(c);
		ConsolidadoDto cx2 = consolidado("AC01001-00");
		assertNotEquals(cx.consolidationId(), cx2.consolidationId());
		assertEquals(0, cx2.totalQty().compareTo(BigDecimal.ONE));

		// --- Envio ---
		assertEquals("ENVIADA", abastecimiento.confirmarEnvio(r1.rfqId(), "CORREO", logistica).status());
		assertThrows(ApiException.class, () -> abastecimiento.confirmarEnvio(r1.rfqId(), "CORREO", logistica));

		// --- Cotizaciones: S1 cotiza ambos, S2 solo AC01001 y mas barato ---
		CotizacionDto cot1 = cotizar(r1.rfqId(), Map.of("AC01001", "10", "AC01002", "5"));
		CotizacionDto cot2 = cotizar(r2.rfqId(), Map.of("AC01001", "8"));
		assertEquals("RESPONDIDA", compras.obtenerRfq(r2.rfqId(), logistica).rfq().status());

		ConsolidadoDetalleDto det = abastecimiento.obtenerConsolidado(cx.consolidationId(), logistica);
		assertEquals(2, det.ofertas().size());
		assertEquals(0, det.ofertas().get(0).unitCost().compareTo(new BigDecimal("8")), "la mas barata primero");
		assertEquals(2, det.consolidado().cotizaciones());
		assertEquals(0, det.consolidado().cantidadCotizada().compareTo(new BigDecimal("7")),
				"la mayor cantidad ofertada, no la suma de las dos ofertas");
		assertNull(cx2.cantidadCotizada(), "sin ofertas todavia");

		// --- Adjudicacion por producto a proveedores distintos ---
		abastecimiento.adjudicar(cx.consolidationId(), linea(cot2, "AC01001"), logistica);
		ApiException ajena = assertThrows(ApiException.class,
				() -> abastecimiento.adjudicar(cx.consolidationId(), linea(cot1, "AC01002"), logistica));
		assertTrue(ajena.getMessage().contains("no corresponde"), ajena.getMessage());
		abastecimiento.adjudicar(cy.consolidationId(), linea(cot1, "AC01002"), logistica);

		List<PorEmitirDto> porEmitir = abastecimiento.listarPorEmitir(logistica);
		assertEquals(2, porEmitir.size());

		// --- OC por cotizacion ---
		OrdenDto oc2 = abastecimiento.emitirOrden(cot2.quotationId(), logistica);
		assertEquals(0, oc2.subtotal().compareTo(new BigDecimal("56.00")));
		assertEquals(0, oc2.total().compareTo(new BigDecimal("66.08")), "por defecto, precios sin IGV + 18%");
		assertNull(oc2.requestId());
		assertTrue(oc2.requestCode().contains(a.codigo) && oc2.requestCode().contains(b.codigo), oc2.requestCode());
		assertThrows(ApiException.class, () -> abastecimiento.emitirOrden(cot2.quotationId(), logistica));
		OrdenDto oc1 = abastecimiento.emitirOrden(cot1.quotationId(), logistica);
		assertEquals(0, oc1.subtotal().compareTo(new BigDecimal("10.00")), "solo lo adjudicado a S1");
		assertEquals("CERRADA", estadoRfq(r1.rfqId()));
		assertEquals("CERRADA", estadoRfq(r2.rfqId()));
		assertEquals(1, jdbc.queryForObject("select count(*) from proveedor_producto where supplier_id = ? and product_id = ?",
				Integer.class, s2, producto("AC01001")));

		// --- Recepcion: primero el urgente (B: 4), luego A (la OC aprobada y liberada, DEC-L061) ---
		aprobarYLiberar(oc2.orderId());
		UUID linea = jdbc.queryForObject("select order_item_id from orden_item where order_id = ?", UUID.class, oc2.orderId());
		recibir(oc2.orderId(), linea, "5");
		assertEquals(0, reservadoPorRecepcion(b.items.get("AC01001-00")).compareTo(new BigDecimal("4")));
		assertEquals(0, reservadoPorRecepcion(a.items.get("AC01001-00")).compareTo(BigDecimal.ONE));
		recibir(oc2.orderId(), linea, "2");
		assertEquals(0, reservadoPorRecepcion(a.items.get("AC01001-00")).compareTo(new BigDecimal("3")));
		assertEquals(0, reservadoPorRecepcion(b.items.get("AC01001-00")).compareTo(new BigDecimal("4")));

		// --- El detalle de compras del REQ ve sus RFQ y necesidades ---
		ComprasDetalleDto detA = compras.obtenerDetalleCompra(a.id, logistica);
		assertEquals(2, detA.rfqs().size());
		assertEquals(2, detA.necesidades().size());
	}

	@Test
	void urgenteDelMismoDiaVaDirectoACompra() {
		// DEC-L092: la caja chica es un modulo aparte; al aprobar ya no se sugiere esa ruta.
		Req req = requerimiento("ADM", "URGENTE", LocalDate.now(LIMA), Map.of("MU03005-00", 4));
		aprobar(req);

		Map<String, Object> n = jdbc.queryForMap("""
				select n.status, n.suggested_reason, c.status as consolidado from necesidad_compra n
				join consolidacion c on c.consolidation_id = n.consolidation_id where n.request_item_id = ?""",
				req.items.get("MU03005-00"));
		assertEquals("COMPRA", n.get("status"));
		assertNull(n.get("suggested_reason"));
		assertEquals("ABIERTA", n.get("consolidado"));
	}

	@Test
	void anularElConsolidadoDevuelveLosFaltantesARuta() {
		UsuarioPrincipal logistica = logistica();
		UUID s = proveedor("20100000013");
		abastecimiento.reemplazarCobertura(producto("AC01003"), List.of(s), logistica);
		Req req = requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("AC01003-00", 2));
		aprobar(req);
		ConsolidadoDto c = consolidado("AC01003-00");
		RfqDto rfq = abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica).get(0);

		assertThrows(ApiException.class, () -> abastecimiento.anularConsolidado(c.consolidationId(), " ", logistica));
		ConsolidadoDetalleDto anulado = abastecimiento.anularConsolidado(c.consolidationId(), "Proveedor sin stock", logistica);

		assertEquals("ANULADA", anulado.consolidado().status());
		assertEquals("CERRADA", estadoRfq(rfq.rfqId()));
		Map<String, Object> nueva = jdbc.queryForMap("""
				select status, suggested_reason, qty from necesidad_compra where request_item_id = ? and status <> 'ANULADA'""",
				req.items.get("AC01003-00"));
		assertEquals("PENDIENTE_RUTA", nueva.get("status"));
		assertEquals("FALLA_PROVEEDOR", nueva.get("suggested_reason"));
		EvaluacionCajaChicaDto ev = cajaChica.evaluar(req.id, logistica);
		assertTrue(ev.motivos().stream().anyMatch(m -> m.codigo().equals("FALLA_PROVEEDOR")), ev.toString());
	}

	@Test
	void ordenConFormatoFlog09IgvElegidoYCondicionesHastaAprobar() throws IOException {
		UsuarioPrincipal logistica = logistica();
		UUID prov = proveedor("20100000015");
		jdbc.update("update proveedor set contact_name = 'Rosa Diaz', phone = '987654321' where supplier_id = ?", prov);
		jdbc.update("""
				insert into proveedor_cuenta_bancaria (supplier_id, bank_name, account_type, currency_code, account_number, cci, is_primary)
				values (?, 'BCP', 'CORRIENTE', 'PEN', '191-1234567-0-11', '00219100123456701155', true),
				       (?, 'Banco de la Nacion', 'DETRACCION', 'PEN', '00-000-123456', null, false)""", prov, prov);
		abastecimiento.reemplazarCobertura(producto("AC01005"), List.of(prov), logistica);
		aprobar(requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("AC01005-00", 4)));
		ConsolidadoDto c = consolidado("AC01005-00");
		RfqDto rfq = abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica).get(0);
		abastecimiento.confirmarEnvio(rfq.rfqId(), "CORREO", logistica);
		CotizacionDto cot = cotizar(rfq.rfqId(), Map.of("AC01005", "25"));
		abastecimiento.adjudicar(c.consolidationId(), linea(cot, "AC01005"), logistica);

		// Por defecto los precios son sin IGV: 4 x 25 = 100 + 18 = 118; el plazo sale de la cotizacion.
		OrdenDto oc = abastecimiento.emitirOrden(cot.quotationId(), logistica);
		assertEquals("MAS_IGV", oc.taxMode());
		assertEquals(0, oc.subtotal().compareTo(new BigDecimal("100.00")));
		assertEquals(0, oc.taxAmount().compareTo(new BigDecimal("18.00")));
		assertEquals(0, oc.total().compareTo(new BigDecimal("118.00")));
		assertEquals("5 días hábiles", oc.deliveryTerm(), "el plazo cotizado, en días hábiles (DEC-L089)");
		assertEquals(5, oc.deliveryDays());
		assertNotNull(oc.fechaEntregaComprometida());
		assertNotNull(oc.deliveryPointId(), "por defecto el almacén central");

		// Precios con IGV incluido: se desglosa hacia atras.
		OrdenDto incluido = ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("IGV_INCLUIDO", 7, "HABILES", null, "Entregar con guía", null), logistica);
		assertEquals(0, incluido.total().compareTo(new BigDecimal("100.00")));
		assertEquals(0, incluido.subtotal().compareTo(new BigDecimal("84.75")));
		assertEquals(0, incluido.taxAmount().compareTo(new BigDecimal("15.25")));
		assertEquals("Entregar con guía", incluido.additionalSpecs());
		OrdenDto noAfecto = ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("NO_AFECTO", 7, "CALENDARIO", null, null, null), logistica);
		assertEquals(0, noAfecto.taxAmount().signum());
		assertEquals(0, noAfecto.total().compareTo(new BigDecimal("100.00")));
		assertThrows(ApiException.class, () -> ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("IVA", 7, "HABILES", null, null, null), logistica));
		UsuarioPrincipal gerencia = new UsuarioPrincipal(usuario("gerencia@logistica.test"), List.of("GERENCIA"));
		assertThrows(ApiException.class, () -> ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("MAS_IGV", 7, "HABILES", null, null, null), gerencia), "solo Logistica las edita");
		ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("MAS_IGV", 7, "HABILES", null, "Entregar con guía", null), logistica);

		// PDF F-LOG-09: por aprobar lo dice y no lleva firmas.
		String borrador = textoPdf(documentos.orden(oc.orderId(), logistica).contenido());
		assertTrue(borrador.contains("F-LOG-09"), borrador);
		assertTrue(borrador.contains("20522095797"), "RUC de TDEM en la cabecera");
		assertTrue(borrador.contains("PENDIENTE DE APROBACIÓN"), borrador);
		assertTrue(borrador.contains("Rosa Diaz") && borrador.contains("987654321"));
		assertTrue(borrador.contains("191-1234567-0-11") && borrador.contains("00-000-123456"));
		assertTrue(borrador.contains("118.00") && borrador.contains("Entregar con guía"), borrador);

		// Aprobada: ya no se editan las condiciones y el PDF deja de ser borrador.
		ordenCompra.decidir(oc.orderId(), new DecidirOrdenRequest("APROBADO", null), gerencia);
		assertThrows(ApiException.class, () -> ordenCompra.editarCondiciones(oc.orderId(),
				new CondicionesOrdenRequest("NO_AFECTO", 7, "HABILES", null, null, null), logistica));
		String aprobada = textoPdf(documentos.orden(oc.orderId(), gerencia).contenido());
		assertFalse(aprobada.contains("PENDIENTE DE APROBACIÓN"), aprobada);
		assertThrows(ApiException.class, () -> documentos.orden(oc.orderId(),
				new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"))));
	}

	@Test
	void datosDeEmpresaParaElFormatoSoloAdmin() {
		UsuarioPrincipal admin = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		EmpresaDatosDto metrin = empresas.listar(admin).stream().filter(e -> "METRIN".equals(e.code())).findFirst().orElseThrow();
		assertNull(metrin.ruc());
		EmpresaDatosDto editada = empresas.editar(metrin.id(),
				new EditarEmpresaRequest("METRIN S.A.C.", "20123456789", "Av. Lima 123", "01 555 5555"), admin)
				.stream().filter(e -> "METRIN".equals(e.code())).findFirst().orElseThrow();
		assertEquals("20123456789", editada.ruc());
		assertEquals("METRIN S.A.C.", editada.nombre());
		assertFalse(editada.tieneFirmaGerencia());
		assertThrows(ApiException.class, () -> empresas.listar(logistica()));
		assertThrows(ApiException.class, () -> empresas.subirImagen(metrin.id(), "firma-gerencia",
				new MockMultipartFile("file", "firma.png", "image/png", new byte[]{1, 2, 3}), admin), "no es una imagen");
		assertThrows(ApiException.class, () -> empresas.subirImagen(metrin.id(), "sello",
				new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}), admin));
	}

	private static String textoPdf(byte[] pdf) throws IOException {
		try (var doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc);
		}
	}

	@Test
	void logisticaEligeAQuienPedirCotizacion() {
		UsuarioPrincipal logistica = logistica();
		UUID amplio = proveedor("20100000016");
		UUID angosto = proveedor("20100000017");
		abastecimiento.reemplazarCobertura(producto("AC01008"), List.of(amplio, angosto), logistica);
		abastecimiento.reemplazarCobertura(producto("AC01009"), List.of(amplio), logistica);
		Map<String, Integer> pedido = new LinkedHashMap<>();
		pedido.put("AC01008-00", 2);
		pedido.put("AC01009-00", 1);
		aprobar(requerimiento("ADM", "APROVISIONAMIENTO", null, pedido));
		List<UUID> ids = List.of(consolidado("AC01008-00").consolidationId(), consolidado("AC01009-00").consolidationId());

		// DEC-L082: primero quien cubre mas productos.
		List<CandidatoRfqDto> candidatos = abastecimiento.candidatos(ids, logistica);
		assertEquals(List.of(amplio, angosto), candidatos.stream().map(CandidatoRfqDto::supplierId).toList());
		assertEquals(2, candidatos.get(0).consolidados().size());
		assertEquals(1, candidatos.get(1).consolidados().size());

		assertThrows(ApiException.class, () -> abastecimiento.prepararSolicitudes(ids, List.of(), logistica));
		ApiException falta = assertThrows(ApiException.class,
				() -> abastecimiento.prepararSolicitudes(ids, List.of(angosto), logistica));
		assertTrue(falta.getMessage().contains("AC01009"), falta.getMessage());

		List<RfqDto> rfqs = abastecimiento.prepararSolicitudes(ids, List.of(amplio), logistica);
		assertEquals(1, rfqs.size(), "solo al elegido, aunque el otro tambien cubra AC01008");
		assertEquals(amplio, rfqs.get(0).supplierId());
		assertThrows(ApiException.class, () -> abastecimiento.candidatos(ids,
				new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"))));
	}

	@Test
	void tdemCompraParaLasTresEmpresasEnUnSoloConsolidado() {
		// DEC-L086: lo que piden TDEM y GOYDEL del mismo producto se compra junto, a nombre de TDEM.
		UsuarioPrincipal logistica = logistica();
		UUID prov = proveedor("20100000021");
		abastecimiento.reemplazarCobertura(producto("AC01010"), List.of(prov), logistica);
		Req tdem = requerimiento("ADM", "APROVISIONAMIENTO", null, Map.of("AC01010-00", 2));
		Req goydel = requerimientoDe("GOYDELSAC", "DEC86-G", Map.of("AC01010-00", 3));
		aprobar(tdem);
		aprobarComoGerencia(goydel);

		ConsolidadoDto c = consolidado("AC01010-00");
		assertEquals(0, c.totalQty().compareTo(new BigDecimal("5")), "un solo consolidado con lo de ambas empresas");
		assertEquals("TDEMSRL", jdbc.queryForObject("""
				select e.code from consolidacion c join empresa e on e.company_id = c.company_id
				 where c.consolidation_id = ?""", String.class, c.consolidationId()));
		assertEquals("GOYDELSAC", jdbc.queryForObject("""
				select e.code from necesidad_compra n join empresa e on e.company_id = n.company_id
				 where n.request_item_id = ?""", String.class, goydel.items.get("AC01010-00")),
				"la necesidad conserva la empresa de su requerimiento");

		List<RfqDto> rfqs = abastecimiento.prepararSolicitudes(List.of(c.consolidationId()), logistica);
		assertEquals(1, rfqs.size());
		assertEquals("TDEMSRL", jdbc.queryForObject("""
				select e.code from rfq r join empresa e on e.company_id = r.company_id where r.rfq_id = ?""",
				String.class, rfqs.get(0).rfqId()), "la solicitud de cotizacion sale de TDEM");
	}

	@Test
	void soloLogisticaGestionaCompras() {
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> abastecimiento.listarNecesidades(solicitante));
		assertThrows(ApiException.class, () -> abastecimiento.listarConsolidados(solicitante));
		assertThrows(ApiException.class, () -> abastecimiento.reemplazarCobertura(producto("AC01004"), List.of(), solicitante));
	}

	// ------------------------------------------------------------------

	private record Req(UUID id, String codigo, UUID approvalId, Map<String, UUID> items) {
	}

	private UsuarioPrincipal logistica() {
		return new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
	}

	private void aprobarYLiberar(UUID orderId) {
		ordenCompra.decidir(orderId, new DecidirOrdenRequest("APROBADO", null),
				new UsuarioPrincipal(usuario("gerencia@logistica.test"), List.of("GERENCIA")));
		ordenCompra.liberar(orderId, new LiberarOrdenRequest(null), logistica());
	}

	private void aprobarComoGerencia(Req req) {
		aprobacionService.decidir(req.approvalId, new DecidirAprobacionRequest("APROBADO", null, null),
				new UsuarioPrincipal(usuario("gerencia@logistica.test"), List.of("GERENCIA")));
	}

	/** REQ de otra empresa, en un CECO propio que se crea si no existe. */
	private Req requerimientoDe(String empresaCode, String ceco, Map<String, Integer> pedido) {
		jdbc.update("""
				insert into ceco (company_id, code, name) select company_id, ?, 'Proyecto ' || ? from empresa where code = ?
				on conflict (company_id, code) do nothing""", ceco, ceco, empresaCode);
		Req base = requerimiento("ADM", "APROVISIONAMIENTO", null, pedido);
		jdbc.update("""
				update requerimiento set company_id = e.company_id, ceco_id = c.ceco_id
				  from empresa e join ceco c on c.company_id = e.company_id and c.code = ?
				 where e.code = ? and requerimiento.request_id = ?""", ceco, empresaCode, base.id);
		return base;
	}

	private void aprobar(Req req) {
		UsuarioPrincipal aprobador = new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"));
		aprobacionService.decidir(req.approvalId, new DecidirAprobacionRequest("APROBADO", null, null), aprobador);
	}

	private ConsolidadoDto consolidado(String codigoVariante) {
		return abastecimiento.listarConsolidados(logistica()).stream()
				.filter(c -> codigoVariante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow();
	}

	private CotizacionDto cotizar(UUID rfqId, Map<String, String> costos) {
		List<CotizacionItemInput> items = compras.obtenerRfq(rfqId, logistica()).items().stream()
				.filter(i -> costos.containsKey(i.skuBase()))
				.map(i -> new CotizacionItemInput(i.rfqItemId(), i.qty(), new BigDecimal(costos.get(i.skuBase())), null, null))
				.toList();
		return compras.registrarCotizacion(rfqId, new RegistrarCotizacionRequest(null, null, null, "PEN", "CREDITO_30",
				"ALMACEN_CLIENTE", 5, null, items), logistica());
	}

	private static UUID linea(CotizacionDto cot, String sku) {
		return cot.items().stream().filter(i -> i.skuBase().equals(sku)).findFirst().orElseThrow().quotationItemId();
	}

	private void recibir(UUID orderId, UUID orderItemId, String qty) {
		recepcion.registrar(orderId, new RegistrarRecepcionRequest(
				List.of(new RecepcionItemInput(orderItemId, new BigDecimal(qty), "CONFORME", null, null)), null, null),
				logistica());
	}

	private Req requerimiento(String ceco, String prioridad, LocalDate requerida, Map<String, Integer> pedido) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = ?", UUID.class, empresa, ceco);
		String codigo = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
		UUID reqId = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority, required_date)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'PENDIENTE_APROBACION', ?, ?) returning request_id""",
				UUID.class, codigo, empresa, cecoId, usuario("solicitante@logistica.test"), prioridad, requerida);
		Map<String, UUID> items = new LinkedHashMap<>();
		pedido.forEach((variante, qty) -> items.put(variante, jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				select ?, v.product_id, v.variant_id, p.base_uom_id, ?
				from producto_variante v join producto p on p.product_id = v.product_id
				where v.internal_variant_code = ? returning request_item_id""", UUID.class, reqId, qty, variante)));
		UUID approvalId = jdbc.queryForObject("""
				insert into aprobacion (entity_type, entity_id) values ('REQUERIMIENTO', ?) returning approval_id""",
				UUID.class, reqId);
		return new Req(reqId, codigo, approvalId, items);
	}

	private void stock(String codigoVariante, int qty) {
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				select a.company_id, a.warehouse_id, v.product_id, v.variant_id, 'AJUSTE', ?, 'AJUSTE', gen_random_uuid(), ?
				from almacen a join empresa e on e.company_id = a.company_id and e.code = 'TDEMSRL'
				cross join producto_variante v where a.code = 'CENTRAL' and v.internal_variant_code = ?""",
				qty, usuario("logistica@logistica.test"), codigoVariante);
	}

	private UUID proveedor(String ruc) {
		return jdbc.queryForObject("insert into proveedor (ruc, legal_name) values (?, ?) returning supplier_id",
				UUID.class, ruc, "Proveedor " + ruc);
	}

	private UUID producto(String skuBase) {
		return jdbc.queryForObject("select product_id from producto where sku_base = ?", UUID.class, skuBase);
	}

	private String estado(Req req) {
		return jdbc.queryForObject("select status from requerimiento where request_id = ?", String.class, req.id);
	}

	private String estadoRfq(UUID rfqId) {
		return jdbc.queryForObject("select status from rfq where rfq_id = ?", String.class, rfqId);
	}

	private BigDecimal reservado(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock "
				+ "where request_item_id = ? and status = 'RESERVADA'", BigDecimal.class, requestItemId);
	}

	private BigDecimal reservadoPorRecepcion(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock "
				+ "where request_item_id = ? and origin = 'RECEPCION_OC' and need_id is not null", BigDecimal.class, requestItemId);
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
