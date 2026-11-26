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
import com.tdem.logistica.modules.compras.OrdenCompraService;
import com.tdem.logistica.modules.compras.dto.*;
import com.tdem.logistica.modules.proveedores.CicloProveedorService;
import com.tdem.logistica.modules.proveedores.DocumentoProveedorService;
import com.tdem.logistica.modules.proveedores.EvaluacionProveedorService;
import com.tdem.logistica.modules.proveedores.FichaProveedorService;
import com.tdem.logistica.modules.proveedores.HistorialComercialService;
import com.tdem.logistica.modules.proveedores.PlantillaEvaluacionService;
import com.tdem.logistica.modules.proveedores.ProveedorService;
import com.tdem.logistica.modules.proveedores.VigenciaEvaluacion;
import com.tdem.logistica.modules.proveedores.dto.GuardarCuentaBancariaRequest;
import com.tdem.logistica.modules.proveedores.dto.*;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Lote 19a (DEC-L069..L071): ciclo de incorporacion, seleccion y evaluacion
 * F-LOG con pesos copiados, vigencias informativas, documentos privados e
 * historial comercial leido de Compras sin romper su contrato.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ProveedoresTest {

	private static EmbeddedPostgres pg;

	@MockitoBean private StorageClient storage;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ProveedorService proveedores;
	@Autowired private FichaProveedorService fichas;
	@Autowired private CicloProveedorService ciclo;
	@Autowired private EvaluacionProveedorService evaluaciones;
	@Autowired private PlantillaEvaluacionService plantillas;
	@Autowired private DocumentoProveedorService documentos;
	@Autowired private HistorialComercialService historial;
	@Autowired private AprobacionService aprobacionService;
	@Autowired private AbastecimientoService abastecimiento;
	@Autowired private ComparativoService comparativo;
	@Autowired private OrdenCompraService ordenCompra;
	@Autowired private RecepcionService recepcion;
	@Autowired private com.tdem.logistica.modules.proveedores.CuentaBancariaService cuentas;

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
	void cicloDeIncorporacionSinRomperCompras() {
		UsuarioPrincipal log = logistica();
		ProveedorDto p = proveedores.crear(solicitud("20600000001", "Nuevo SAC", null), log);
		assertEquals("BORRADOR", p.status());
		assertFalse(p.active());
		assertNotNull(p.createdAt());
		// Un borrador no existe para Compras.
		assertTrue(proveedores.listar(log).stream().noneMatch(x -> x.supplierId().equals(p.supplierId())));
		assertTrue(fichas.panel(log).proveedores().stream().anyMatch(x -> x.supplierId().equals(p.supplierId())));
		// DEC-L081: sin empresa indicada se registra en TDEM; al editar se puede cambiar.
		assertEquals("TDEMSRL", p.empresaCode());
		GuardarProveedorRequest base = solicitud("20600000001", "Nuevo SAC", null);
		GuardarProveedorRequest goydel = new GuardarProveedorRequest(base.ruc(), base.legalName(), null, base.contactName(),
				null, null, null, null, null, base.address(), base.city(), base.supplierType(), null,
				base.offeringDescription(), base.paymentCondition(), base.creditDays(), base.leadDays(), base.warrantyTerms(),
				"GOYDELSAC");
		assertEquals("GOYDELSAC", proveedores.actualizar(p.supplierId(), goydel, log).empresaCode());
		assertEquals("GOYDELSAC", fichas.panel(log).proveedores().stream()
				.filter(x -> x.supplierId().equals(p.supplierId())).findFirst().orElseThrow().empresaCode());
		assertThrows(ApiException.class, () -> proveedores.actualizar(p.supplierId(), new GuardarProveedorRequest(
				base.ruc(), base.legalName(), null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, "NO-EXISTE"), log));

		// RUC unico global, con el nombre y estado del que ya existe.
		ApiException duplicado = assertThrows(ApiException.class,
				() -> proveedores.crear(solicitud("20600000001", "Otro", "a@b.pe"), log));
		assertTrue(duplicado.getMessage().contains("Nuevo SAC"), duplicado.getMessage());

		// Sin medio de contacto no se envia; con correo si.
		assertThrows(ApiException.class, () -> ciclo.enviar(p.supplierId(), log));
		proveedores.actualizar(p.supplierId(), solicitud("20600000001", "Nuevo SAC", "ventas@nuevo.pe"), log);
		assertEquals("PENDIENTE_REVISION", ciclo.enviar(p.supplierId(), log).status());
		assertEquals(1, fichas.panel(log).kpis().enRevision());

		// Observar y rechazar exigen motivo; observado se corrige y vuelve a revision.
		assertThrows(ApiException.class, () -> ciclo.decidir(p.supplierId(), "OBSERVAR", " ", log));
		ProveedorDto observado = ciclo.decidir(p.supplierId(), "OBSERVAR", "Falta ficha RUC", log);
		assertEquals("OBSERVADO", observado.status());
		assertEquals("Falta ficha RUC", observado.statusReason());
		assertEquals("PENDIENTE_REVISION", ciclo.enviar(p.supplierId(), log).status());
		assertEquals("RECHAZADO", ciclo.decidir(p.supplierId(), "RECHAZAR", "No cumple", log).status());
		assertThrows(ApiException.class, () -> proveedores.actualizar(p.supplierId(),
				solicitud("20600000001", "Nuevo SAC", "ventas@nuevo.pe"), log), "un rechazado no se edita");
		assertThrows(ApiException.class, () -> ciclo.reabrir(p.supplierId(), "", log));
		assertEquals("BORRADOR", ciclo.reabrir(p.supplierId(), "Presento sus documentos", log).status());
		ciclo.enviar(p.supplierId(), log);

		// Habilitar con advertencias (sin seleccion ni documentos) exige motivo.
		FichaProveedorDto ficha = fichas.ficha(p.supplierId(), log);
		assertFalse(ficha.advertencias().isEmpty());
		ApiException sinMotivo = assertThrows(ApiException.class,
				() -> ciclo.decidir(p.supplierId(), "HABILITAR", null, log));
		assertEquals(CicloProveedorService.HABILITAR_CON_ADVERTENCIAS, sinMotivo.getCode());
		ProveedorDto habilitado = ciclo.decidir(p.supplierId(), "HABILITAR", "Proveedor unico en la zona", log);
		assertEquals("HABILITADO", habilitado.status());
		assertTrue(habilitado.active());
		assertTrue(proveedores.listar(log).stream().anyMatch(x -> x.supplierId().equals(p.supplierId())));

		// Desactivar exige motivo; el check de la BD impide activo sin habilitar.
		assertThrows(ApiException.class, () -> ciclo.cambiarActivo(p.supplierId(), false, null, log));
		assertFalse(ciclo.cambiarActivo(p.supplierId(), false, "Cerro", log).active());
		assertThrows(Exception.class, () -> jdbc.update(
				"update proveedor set status = 'BORRADOR', active = true where supplier_id = ?", p.supplierId()));

		// Todo queda auditado y se ve en el historial.
		List<String> acciones = fichas.historial(p.supplierId(), log).stream()
				.map(SolicitudesProveedor.Historial::accion).toList();
		assertTrue(acciones.containsAll(List.of("CREAR", "EDITAR", "ENVIAR_REVISION", "OBSERVAR", "RECHAZAR",
				"REABRIR", "HABILITAR", "DESACTIVAR")), acciones.toString());
		String advertencias = jdbc.queryForObject("""
				select despues ->> 'advertencias' from log_cambios
				 where entidad = 'PROVEEDOR' and entidad_id = ? and accion = 'HABILITAR'""", String.class, p.supplierId());
		assertNotNull(advertencias);

		// Solo Logistica y Admin.
		UsuarioPrincipal sol = solicitante();
		assertThrows(ApiException.class, () -> fichas.panel(sol));
		assertThrows(ApiException.class, () -> fichas.ficha(p.supplierId(), sol));
		assertThrows(ApiException.class, () -> proveedores.crear(solicitud("20600000009", "X", null), sol));
		assertThrows(ApiException.class, () -> ciclo.enviar(p.supplierId(), sol));
		assertThrows(ApiException.class, () -> evaluaciones.listar(p.supplierId(), sol));
		assertThrows(ApiException.class, () -> plantillas.configuracion(sol));
		assertThrows(ApiException.class, () -> historial.compras(p.supplierId(), sol));
		assertNotNull(fichas.panel(admin()));
	}

	@Test
	void losProveedoresQueYaExistianQuedanHabilitados() {
		UUID id = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20600000002', 'Antiguo') returning supplier_id",
				UUID.class);
		ProveedorDto p = proveedores.obtener(id, logistica());
		assertEquals("HABILITADO", p.status());
		assertTrue(p.active());
		assertEquals(VigenciaEvaluacion.SIN_EVALUACION, fichas.ficha(id, logistica()).vigencia());
	}

	@Test
	void seleccionYEvaluacionConPesosCopiados() {
		UsuarioPrincipal log = logistica();
		UUID id = proveedores.crear(solicitud("20600000003", "Evaluado SAC", "e@e.pe"), log).supplierId();
		PlantillaDto f03 = plantillas.configuracion(log).plantillas().stream()
				.filter(t -> "SELECCION".equals(t.kind())).findFirst().orElseThrow();
		assertEquals("F-LOG-03", f03.code());
		assertEquals(9, f03.criterios().size());
		assertEquals(0, new BigDecimal("6").compareTo(f03.approvalThreshold()));

		// 50*10 + 7*5 + 15*7 + 10*10 + 3*0 + 4*7 + 4*10 + 3*5 + 4*10 = 863 / 100 = 8.63
		double[] notas = {10, 5, 7, 10, 0, 7, 10, 5, 10};
		LocalDate hoy = VigenciaEvaluacion.hoy();
		EvaluacionDto sel = evaluaciones.registrar(id, evaluacion("SELECCION", f03, i -> notas[i], hoy, "SI", "SI"), log);
		assertEquals(0, new BigDecimal("8.63").compareTo(sel.finalScore()));
		assertEquals("APROBADO", sel.result());
		assertEquals(hoy.plusMonths(12), sel.nextEvaluationDate());
		assertEquals("Mejor (barato) que el promedio del mercado", sel.criterios().get(2).scoreLabel());

		// Con nota suficiente pero sin conformidad del area: pendiente. Puntaje fuera de las opciones: error.
		EvaluacionDto pendiente = evaluaciones.registrar(id, evaluacion("SELECCION", f03, i -> notas[i], hoy, "SI", "NO"), log);
		assertEquals("PENDIENTE_CONFORMIDAD", pendiente.result());
		assertThrows(ApiException.class, () -> evaluaciones.registrar(id,
				evaluacion("SELECCION", f03, i -> 4, hoy, "SI", "SI"), log));
		assertThrows(ApiException.class, () -> evaluaciones.registrar(id,
				evaluacion("SELECCION", f03, i -> notas[i], hoy.plusDays(1), "SI", "SI"), log), "fecha futura");
		// Desempeno solo para habilitados.
		PlantillaDto f07 = plantillas.configuracion(log).plantillas().stream()
				.filter(t -> "DESEMPENO".equals(t.kind())).findFirst().orElseThrow();
		assertThrows(ApiException.class, () -> evaluaciones.registrar(id,
				evaluacion("DESEMPENO", f07, i -> 10, hoy, "SI", "SI"), log));

		// Pesos que no suman 100: rechazados. Editar la plantilla no cambia lo registrado.
		List<ConfiguracionProveedores.CriterioPlantilla> mal = new ArrayList<>();
		f03.criterios().forEach(c -> mal.add(new ConfiguracionProveedores.CriterioPlantilla(c.criterionId(), c.name(),
				c.weight(), opciones(c))));
		mal.set(0, new ConfiguracionProveedores.CriterioPlantilla(mal.get(0).criterionId(), "Requisitos tecnicos",
				new BigDecimal("40"), mal.get(0).opciones()));
		assertThrows(ApiException.class, () -> plantillas.guardarPlantilla("SELECCION", guardar(f03, mal), log));
		List<ConfiguracionProveedores.CriterioPlantilla> bien = new ArrayList<>(mal);
		bien.add(new ConfiguracionProveedores.CriterioPlantilla(null, "Visita a planta", new BigDecimal("10"), List.of()));
		PlantillaDto v8 = plantillas.guardarPlantilla("SELECCION", guardar(f03, bien), log);
		assertEquals(f03.version() + 1, v8.version());
		assertEquals(10, v8.criterios().size());
		EvaluacionDto historica = evaluaciones.listar(id, log).stream()
				.filter(e -> e.evaluationId().equals(sel.evaluationId())).findFirst().orElseThrow();
		assertEquals(0, new BigDecimal("50").compareTo(historica.criterios().get(0).weight()));
		assertEquals(f03.version(), historica.templateVersion());
		assertEquals(0, new BigDecimal("8.63").compareTo(historica.finalScore()));

		// Anular conserva la evaluacion con su motivo y la saca de la vigencia.
		assertThrows(ApiException.class, () -> evaluaciones.anular(pendiente.evaluationId(), " ", log));
		EvaluacionDto anulada = evaluaciones.anular(pendiente.evaluationId(), "Error de digitacion", log);
		assertEquals("ANULADA", anulada.status());
		assertEquals(1L, evaluaciones.listar(id, log).stream().filter(e -> "REGISTRADA".equals(e.status())).count());
		assertEquals(2, evaluaciones.listar(id, log).size(), "la anulada sigue en el historial");
		FichaProveedorDto ficha = fichas.ficha(id, log);
		assertEquals(sel.evaluationId(), ficha.ultimaSeleccion().evaluationId());
		assertEquals(VigenciaEvaluacion.VIGENTE, ficha.vigencia());
		// Una evaluacion vencida solo advierte: el estado del proveedor no cambia.
		jdbc.update("update evaluacion_proveedor set evaluated_at = ?, next_evaluation_date = ? where evaluation_id = ?",
				hoy.minusMonths(13), hoy.minusDays(1), sel.evaluationId());
		FichaProveedorDto vencida = fichas.ficha(id, log);
		assertEquals(VigenciaEvaluacion.VENCIDO, vencida.vigencia());
		assertEquals("BORRADOR", vencida.proveedor().status());
	}

	@Test
	void vigenciaEnSusBordes() {
		LocalDate hoy = LocalDate.of(2026, 11, 18);
		assertEquals(VigenciaEvaluacion.SIN_EVALUACION, VigenciaEvaluacion.estado(null, hoy, 30));
		assertEquals(VigenciaEvaluacion.VENCIDO, VigenciaEvaluacion.estado(hoy.minusDays(1), hoy, 30));
		assertEquals(VigenciaEvaluacion.PROXIMO_A_VENCER, VigenciaEvaluacion.estado(hoy, hoy, 30));
		assertEquals(VigenciaEvaluacion.PROXIMO_A_VENCER, VigenciaEvaluacion.estado(hoy.plusDays(30), hoy, 30));
		assertEquals(VigenciaEvaluacion.VIGENTE, VigenciaEvaluacion.estado(hoy.plusDays(31), hoy, 30));
	}

	@Test
	void documentosPrivadosConRequisitosYVencimiento() {
		when(storage.subirPrivado(anyString(), any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
		when(storage.leerPrivado(anyString())).thenReturn(new byte[]{1, 2, 3});
		UsuarioPrincipal log = logistica();
		UUID id = proveedores.crear(solicitud("20600000004", "Documentado SAC", "d@d.pe"), log).supplierId();
		DocumentosProveedorDto inicial = documentos.listar(id, log);
		DocumentosProveedorDto.Requisito ruc = inicial.requisitos().stream()
				.filter(r -> r.name().startsWith("Ficha RUC")).findFirst().orElseThrow();
		DocumentosProveedorDto.Requisito iso = inicial.requisitos().stream()
				.filter(r -> r.name().startsWith("Certificación ISO")).findFirst().orElseThrow();
		assertEquals("FALTA", ruc.estado());
		assertTrue(fichas.ficha(id, log).advertencias().stream().anyMatch(a -> a.contains("Ficha RUC")));

		MockMultipartFile pdf = new MockMultipartFile("file", "ruc.pdf", "application/pdf", new byte[]{1, 2, 3});
		DocumentosProveedorDto conRuc = documentos.subir(id, pdf, ruc.requirementId(), null, null, null, null, log);
		assertEquals("VIGENTE", requisito(conRuc, ruc.requirementId()).estado());
		assertTrue(fichas.ficha(id, log).advertencias().stream().noneMatch(a -> a.contains("Ficha RUC")));
		String url = jdbc.queryForObject("""
				select doc.file_url from proveedor_documento d join documento doc on doc.document_id = d.document_id
				 where d.supplier_id = ?""", String.class, id);
		assertTrue(url.startsWith("privado:proveedores/" + id + "/"), url);

		// Con vencimiento exige fecha; vencido se marca; formato no permitido se rechaza.
		assertThrows(ApiException.class, () -> documentos.subir(id, pdf, iso.requirementId(), null, null, null, null, log));
		LocalDate hoy = VigenciaEvaluacion.hoy();
		DocumentosProveedorDto conIso = documentos.subir(id, pdf, iso.requirementId(), null, hoy.minusYears(1),
				hoy.plusDays(10), null, log);
		assertEquals(VigenciaEvaluacion.PROXIMO_A_VENCER, requisito(conIso, iso.requirementId()).estado());
		assertThrows(ApiException.class, () -> documentos.subir(id,
				new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}), null, null, null, null,
				null, log));

		// Descarga por el backend, solo Logistica/Admin.
		UUID docId = requisito(conRuc, ruc.requirementId()).supplierDocumentId();
		assertArrayEquals(new byte[]{1, 2, 3}, documentos.descargar(docId, log).contenido());
		assertThrows(ApiException.class, () -> documentos.descargar(docId, solicitante()));

		// Anular exige motivo, conserva el registro y vuelve a faltar el requisito.
		assertThrows(ApiException.class, () -> documentos.anular(docId, null, log));
		DocumentosProveedorDto anulado = documentos.anular(docId, "Ficha desactualizada", log);
		assertEquals("FALTA", requisito(anulado, ruc.requirementId()).estado());
		assertTrue(anulado.documentos().stream().anyMatch(d -> d.supplierDocumentId().equals(docId)
				&& "ANULADO".equals(d.status()) && "Ficha desactualizada".equals(d.voidReason())));
	}

	@Test
	void historialComercialLeidoDeCompras() {
		UsuarioPrincipal log = logistica();
		UUID prov = jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20600000005', 'Historico SAC') returning supplier_id",
				UUID.class);
		OrdenDto oc = emitir("AC01003-00", 3, prov);
		ordenCompra.decidir(oc.orderId(), new DecidirOrdenRequest("APROBADO", null), gerencia());
		RecepcionOrdenDto ro = recepcion.obtenerRecepcionOrden(oc.orderId(), log);
		recepcion.registrar(oc.orderId(), new RegistrarRecepcionRequest(List.of(new RecepcionItemInput(
				ro.items().get(0).orderItemId(), BigDecimal.valueOf(2), "CONFORME", null, null)), null, null), log);

		ComprasProveedorDto compras = historial.compras(prov, log);
		assertEquals(1, compras.ordenes().size());
		assertEquals(1, compras.cotizaciones().size());
		assertEquals("PEN", compras.totales().get(0).moneda());
		assertEquals(0, new BigDecimal("70.80").compareTo(compras.totales().get(0).total()), "3 x 20 + IGV 18%");
		assertEquals(1, compras.productos().size());
		assertEquals(0, new BigDecimal("20").compareTo(compras.productos().get(0).ultimoPrecio()));
		assertNotNull(compras.ordenes().get(0).primeraRecepcion());

		DesempenoProveedorDto d = historial.desempeno(prov, log);
		assertEquals(1, d.ordenes());
		assertEquals(1, d.ordenesConRecepcion());
		assertEquals(0, new BigDecimal("66.7").compareTo(d.atencionPct()));
		assertEquals(1, d.recepciones());
		assertEquals(1, d.cotizacionesAdjudicadas());
		// Plazo ofrecido de 3 dias en la cotizacion: recibido el mismo dia, dentro del plazo (referencial).
		assertEquals(1, d.plazoOfrecido().ordenesConPlazo());
		assertEquals(1, d.plazoOfrecido().dentroDelPlazo());
		assertTrue(d.pendientes().stream().anyMatch(x -> x.indicador().contains("fecha comprometida")));

		// La ficha muestra sus productos (habilitado al emitir la OC) y el panel los busca.
		assertTrue(fichas.ficha(prov, log).productos().stream().anyMatch(x -> "AC01003".equals(x.skuBase())));
		assertTrue(fichas.panel(log).proveedores().stream()
				.filter(x -> x.supplierId().equals(prov)).findFirst().orElseThrow().busqueda().contains("AC01003"));
	}

	@Test
	void categoriasYProductosDesdeLaFicha() {
		UsuarioPrincipal log = logistica();
		UUID id = proveedores.crear(solicitud("20600000006", "Clasificado SAC", "c@c.pe"), log).supplierId();
		Map<String, Object> fam = jdbc.queryForMap("select product_type_id, family_id from familia_producto limit 1");
		UUID tipo = (UUID) fam.get("product_type_id");
		UUID familia = (UUID) fam.get("family_id");
		proveedores.guardarCategorias(id, List.of(new SolicitudesProveedor.CategoriaItem(tipo, familia),
				new SolicitudesProveedor.CategoriaItem(tipo, familia)), log);
		assertEquals(1, fichas.ficha(id, log).categorias().size());
		assertThrows(ApiException.class, () -> proveedores.guardarCategorias(id,
				List.of(new SolicitudesProveedor.CategoriaItem(UUID.randomUUID(), null)), log));

		UUID producto = proveedores.productosDisponibles(log).get(0).productId();
		proveedores.guardarProductos(id, List.of(producto), log);
		assertEquals(1, fichas.ficha(id, log).productos().size());
		proveedores.guardarProductos(id, List.of(), log);
		assertEquals(0, fichas.ficha(id, log).productos().size());
		assertThrows(ApiException.class, () -> proveedores.guardarProductos(id, List.of(UUID.randomUUID()), log));
	}

	/** DEC-L078: cuentas bancarias solo para Logistica/Admin, con el numero enmascarado en la auditoria. */
	@Test
	void cuentasBancariasSoloLogisticaYAuditoriaEnmascarada() {
		UsuarioPrincipal log = logistica();
		UUID id = proveedores.crear(solicitud("20600000007", "Con Cuentas SAC", "b@b.pe"), log).supplierId();
		var bcp = new GuardarCuentaBancariaRequest("bcp", "CORRIENTE", "PEN", "191-2561841-0-80", "00219100256184108054", true);
		var lista = cuentas.crear(id, bcp, log);
		assertEquals(1, lista.size());
		assertEquals("BCP", lista.get(0).banco());
		assertTrue(lista.get(0).principal());

		// La segunda principal desplaza a la primera; la misma cuenta no se repite.
		var bn = new GuardarCuentaBancariaRequest("Banco de la Nacion", "DETRACCION", "PEN", "00-058-101214", null, true);
		lista = cuentas.crear(id, bn, log);
		assertEquals(1, lista.stream().filter(c -> c.principal()).count());
		assertThrows(ApiException.class, () -> cuentas.crear(id, bcp, log), "duplicada");
		assertThrows(ApiException.class, () -> cuentas.crear(id,
				new GuardarCuentaBancariaRequest("BBVA", "AHORROS", "XYZ", "0011-0317", null, false), log), "moneda");

		// Nadie mas las ve.
		assertThrows(ApiException.class, () -> cuentas.listar(id, gerencia()));
		assertThrows(ApiException.class, () -> cuentas.listar(id, solicitante()));
		assertEquals(2, cuentas.listar(id, admin()).size());

		// Editar y quitar; la auditoria nunca guarda el numero completo.
		UUID cuenta = lista.stream().filter(c -> c.banco().equals("BCP")).findFirst().orElseThrow().id();
		cuentas.actualizar(cuenta, new GuardarCuentaBancariaRequest("BCP", "AHORROS", "USD", "191-2561841-0-80",
				null, false), log);
		assertEquals("USD", cuentas.listar(id, log).stream().filter(c -> c.id().equals(cuenta)).findFirst()
				.orElseThrow().moneda());
		assertEquals(1, cuentas.eliminar(cuenta, log).size());
		String auditoria = String.join(" | ", jdbc.queryForList(
				"select coalesce(antes::text, '') || coalesce(despues::text, '') from log_cambios where entidad_id = ? "
						+ "and accion like '%CUENTA_BANCARIA'", String.class, id));
		assertTrue(auditoria.contains("****1080"), auditoria);
		assertFalse(auditoria.contains("2561841"), "el numero completo no queda en la auditoria");
		assertFalse(auditoria.contains("00219100256184108054"));
	}

	// ---------------------------------------------------------------- apoyo

	private static GuardarProveedorRequest solicitud(String ruc, String nombre, String correo) {
		return new GuardarProveedorRequest(ruc, nombre, null, "Contacto", null, correo, null, null, null, "Av. Lima 1",
				"Lima", "NO_CRITICO", null, "Ferreteria", "Credito 30 dias", 30, 3, "12 meses", null);
	}

	private static RegistrarEvaluacionRequest evaluacion(String kind, PlantillaDto t, Function<Integer, Number> nota,
														 LocalDate fecha, String conformidadLog, String conformidadArea) {
		List<RegistrarEvaluacionRequest.Puntaje> puntajes = new ArrayList<>();
		for (int i = 0; i < t.criterios().size(); i++) {
			puntajes.add(new RegistrarEvaluacionRequest.Puntaje(t.criterios().get(i).criterionId(),
					new BigDecimal(nota.apply(i).toString()), null));
		}
		return new RegistrarEvaluacionRequest(kind, fecha, "Logistica", "Ferreteria", "Jefe de area", conformidadLog,
				conformidadArea, null, null, puntajes);
	}

	private static List<ConfiguracionProveedores.OpcionPlantilla> opciones(PlantillaDto.Criterio c) {
		return c.opciones().stream().map(o -> new ConfiguracionProveedores.OpcionPlantilla(o.score(), o.label())).toList();
	}

	private static ConfiguracionProveedores.GuardarPlantilla guardar(PlantillaDto t,
																	List<ConfiguracionProveedores.CriterioPlantilla> criterios) {
		return new ConfiguracionProveedores.GuardarPlantilla(t.name(), t.code(), t.scaleMax(), t.approvalThreshold(),
				t.validityMonths(), t.alertDays(), criterios);
	}

	private static DocumentosProveedorDto.Requisito requisito(DocumentosProveedorDto d, UUID requirementId) {
		return d.requisitos().stream().filter(r -> r.requirementId().equals(requirementId)).findFirst().orElseThrow();
	}

	/** REQ aprobado sin stock -> consolidado -> una oferta del proveedor -> adjudicado -> OC. */
	private OrdenDto emitir(String variante, int qty, UUID prov) {
		UsuarioPrincipal log = logistica();
		UUID req = requerimiento(Map.of(variante, qty), usuario("solicitante@logistica.test"));
		UUID approvalId = jdbc.queryForObject(
				"select approval_id from aprobacion where entity_id = ? and status = 'PENDIENTE'", UUID.class, req);
		aprobacionService.decidir(approvalId, new DecidirAprobacionRequest("APROBADO", null, null), admin());
		UUID cons = abastecimiento.listarConsolidados(log).stream()
				.filter(c -> variante.equals(c.codigoVariante()) && "ABIERTA".equals(c.status()))
				.findFirst().orElseThrow().consolidationId();
		ComparativoDto c = comparativo.registrar(new RegistrarComparativoRequest(prov, List.of(cons), null, null, null, "PEN",
				"CONTADO", "AGENCIA", 3, null, null, false,
				List.of(new RegistrarComparativoRequest.Precio(cons, new BigDecimal("20"), null, null, null, null, null))), log);
		UUID item = c.proveedores().get(0).precios().get(0).quotationItemId();
		comparativo.adjudicar(new AdjudicarComparativoRequest(List.of(cons),
				List.of(new AdjudicarComparativoRequest.Eleccion(cons, item))), log);
		return abastecimiento.emitirOrden(c.proveedores().get(0).quotationId(), log);
	}

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
