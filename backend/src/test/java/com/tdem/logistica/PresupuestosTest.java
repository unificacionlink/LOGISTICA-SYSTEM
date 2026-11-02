package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.presupuestos.PresupuestoService;
import com.tdem.logistica.modules.presupuestos.dto.*;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 14 (DEC-L036): periodos, presupuesto SKU x 6 meses, emision, aprobacion y versiones. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class PresupuestosTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private PresupuestoService service;

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
	void cicloCompletoConPreciosCongeladosYRevision() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal pyc = principal("pyc@logistica.test", "PYC");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UsuarioPrincipal gerencia = principal("gerencia@logistica.test", "GERENCIA");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UUID ceco = ceco("TDEMSRL", 0);
		UUID tdem = empresa("TDEMSRL");
		UUID ac1 = producto("AC01001");
		UUID ac3 = producto("AC01003");
		precio(tdem, ac1, "15");

		// --- Periodo: solo PYC lo habilita, una vez por CECO + semestre ---
		assertThrows(ApiException.class, () -> service.crearPeriodo(new CrearPeriodoRequest(ceco, 2027, 1), solicitante));
		assertThrows(ApiException.class, () -> service.crearPeriodo(new CrearPeriodoRequest(ceco, 2027, 1), logistica));
		PeriodoDto periodo = service.crearPeriodo(new CrearPeriodoRequest(ceco, 2027, 1), pyc);
		assertThrows(ApiException.class, () -> service.crearPeriodo(new CrearPeriodoRequest(ceco, 2027, 1), pyc));
		assertTrue(service.listarPeriodos(solicitante).puedeCrear());
		assertFalse(service.listarPeriodos(logistica).puedeCrear());
		assertFalse(service.listarPeriodos(pyc).cecosConfigurables().isEmpty());

		// --- Borrador: uno por periodo; Logistica solo consulta ---
		PresupuestoDetalleDto b = service.crear(new CrearPresupuestoRequest(periodo.periodId(), "Obra 2027"), solicitante);
		assertEquals("BORRADOR", b.estado());
		assertTrue(b.codigo().startsWith("PRE-"));
		assertThrows(ApiException.class, () -> service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), pyc));
		assertThrows(ApiException.class, () -> service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), logistica));
		UUID id = b.id();

		// --- Programacion SKU x 6 meses ---
		assertThrows(ApiException.class, () -> service.guardarLineas(id, List.of(linea(ac1, 0, 0, 0, 0, 0, 0)), solicitante));
		assertThrows(ApiException.class, () -> service.guardarLineas(id,
				List.of(linea(ac1, 1, 0, 0, 0, 0, 0), linea(ac1, 2, 0, 0, 0, 0, 0)), solicitante));
		assertThrows(ApiException.class, () -> service.guardarLineas(id, List.of(linea(ac1, 1, 0, 0, 0, 0, 0)), logistica));
		b = service.guardarLineas(id, List.of(linea(ac1, 10, 0, 0, 0, 0, 5), linea(ac3, 0, 2, 0, 0, 0, 0)), solicitante);
		assertEquals(2, b.lineas().size());
		assertEquals(1, b.lineasSinPrecio());
		assertTrue(b.permisos().emitir());
		assertFalse(service.obtener(id, logistica).permisos().editar());

		// --- Emitir: exige aprobador del CECO y precio de venta en todas las partidas ---
		assertThrows(ApiException.class, () -> service.emitir(id, solicitante), "sin aprobador del CECO");
		jdbc.update("update ceco set approver_user_id = ? where ceco_id = ?", aprobador.userId(), ceco);
		assertThrows(ApiException.class, () -> service.emitir(id, solicitante), "AC01003 sin precio");
		precio(tdem, ac3, "40");
		b = service.emitir(id, solicitante);
		assertEquals("EMITIDO", b.estado());
		assertEquals("Aprobador de Prueba", b.aprobador());
		assertFalse(b.permisos().editar());

		// --- Deciden el aprobador asignado y Gerencia del CECO; nunca el solicitante ni PYC ---
		assertThrows(ApiException.class, () -> service.decidir(id, "APROBADO", null, solicitante));
		assertThrows(ApiException.class, () -> service.decidir(id, "APROBADO", null, pyc));
		assertTrue(service.obtener(id, gerencia).permisos().decidir());
		assertTrue(service.listar(null, null, null, aprobador).getFirst().porDecidir());
		assertThrows(ApiException.class, () -> service.decidir(id, "OBSERVADO", " ", aprobador));
		b = service.decidir(id, "OBSERVADO", "Falta mano de obra", aprobador);
		assertEquals("OBSERVADO", b.estado());
		assertEquals("Falta mano de obra", b.motivoDecision());

		// --- Observado: se corrige y se vuelve a emitir ---
		service.guardarLineas(id, List.of(linea(ac1, 10, 0, 0, 0, 0, 5), linea(ac3, 0, 3, 0, 0, 0, 0)), solicitante);
		service.emitir(id, solicitante);
		b = service.decidir(id, "APROBADO", null, aprobador);
		assertEquals("APROBADO", b.estado());
		assertEquals(1, b.versionVigente());
		assertEquals(0, new BigDecimal("345.00").compareTo(b.total()), "15 x 15 + 3 x 40");
		assertTrue(b.lineas().stream().allMatch(LineaPresupuestoDto::precioCongelado));
		assertEquals("SIN_COMPRA", jdbc.queryForObject("""
				select purchase_status from presupuesto_version_linea vl
				join presupuesto_version v on v.budget_version_id = vl.budget_version_id
				where v.budget_id = ? and vl.product_id = ?""", String.class, id, ac1));

		// --- El precio congelado no cambia aunque cambie el de venta ---
		jdbc.update("update precio_venta_producto set sale_price = 20 where company_id = ? and product_id = ?", tdem, ac1);
		assertEquals(0, new BigDecimal("345.00").compareTo(service.obtener(id, solicitante).total()));

		// --- Revision: sigue rigiendo la version 1 hasta aprobar la 2 ---
		assertThrows(ApiException.class, () -> service.modificar(id, logistica));
		b = service.modificar(id, solicitante);
		assertEquals("MODIFICADO", b.estado());
		assertEquals(1, b.versionVigente());
		assertEquals(0, new BigDecimal("420.00").compareTo(b.total()), "precio vigente 20 mientras se edita");
		service.emitir(id, solicitante);
		assertEquals(2, service.decidir(id, "APROBADO", null, gerencia).versionVigente());

		// --- Anular: el decisor con motivo; libera el periodo ---
		assertThrows(ApiException.class, () -> service.anular(id, "No va", solicitante));
		assertEquals("ANULADO", service.anular(id, "Proyecto cancelado", aprobador).estado());
		PresupuestoDetalleDto otro = service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), solicitante);
		assertNotEquals(b.codigo(), otro.codigo());
		// El redactor puede anular su propio borrador.
		assertEquals("ANULADO", service.anular(otro.id(), "Duplicado", solicitante).estado());

		// --- Periodo cerrado: solo consulta ---
		service.cerrarPeriodo(periodo.periodId(), true, pyc);
		assertThrows(ApiException.class, () -> service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), solicitante));
		assertTrue(service.cerrarPeriodo(periodo.periodId(), false, pyc).presupuestoId() == null);

		List<HistorialPresupuestoDto> historial = service.obtener(id, solicitante).historial();
		assertEquals(List.of("CREAR", "EMITIR", "OBSERVAR", "EMITIR", "APROBAR", "MODIFICAR", "EMITIR", "APROBAR", "ANULAR"),
				historial.stream().map(HistorialPresupuestoDto::accion).toList());
	}

	@Test
	void ambitoYVisibilidad() {
		UsuarioPrincipal pyc = principal("pyc@logistica.test", "PYC");
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UsuarioPrincipal admin = principal("admin@logistica.test", "ADMIN");
		UUID ceco = ceco("TDEMSRL", 1);
		UUID ajeno = jdbc.queryForObject("""
				insert into ceco (company_id, code, name) select company_id, 'G-PRUEBA', 'CECO AJENO' from empresa
				where code = 'GOYDELSAC' returning ceco_id""", UUID.class);

		// PYC solo habilita periodos de sus CECOs.
		assertThrows(ApiException.class, () -> service.crearPeriodo(new CrearPeriodoRequest(ajeno, 2028, 2), pyc));
		PeriodoDto periodo = service.crearPeriodo(new CrearPeriodoRequest(ceco, 2028, 2), pyc);

		// El APROBADOR sin el CECO ni asignacion no lo ve ni redacta.
		assertTrue(service.listarPeriodos(aprobador).periodos().stream().noneMatch(p -> p.periodId().equals(periodo.periodId())));
		assertThrows(ApiException.class, () -> service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), aprobador));

		PresupuestoDetalleDto b = service.crear(new CrearPresupuestoRequest(periodo.periodId(), null), solicitante);
		assertThrows(ApiException.class, () -> service.obtener(b.id(), aprobador));
		assertTrue(service.listar(null, null, null, aprobador).stream().noneMatch(r -> r.id().equals(b.id())));

		// Admin ve todo pero no redacta ni decide; tampoco habilita periodos.
		assertTrue(service.listar(2028, 2, null, admin).stream().anyMatch(r -> r.id().equals(b.id())));
		assertFalse(service.obtener(b.id(), admin).permisos().editar());
		assertThrows(ApiException.class, () -> service.guardarLineas(b.id(), List.of(), admin));
		assertThrows(ApiException.class, () -> service.crearPeriodo(new CrearPeriodoRequest(ceco, 2029, 1), admin));

		// Asignado como aprobador del CECO, ya lo ve.
		jdbc.update("update ceco set approver_user_id = ? where ceco_id = ?", aprobador.userId(), ceco);
		assertEquals(b.id(), service.obtener(b.id(), aprobador).id());
	}

	// ------------------------------------------------------------------

	private static LineaRequest linea(UUID productId, int... meses) {
		return new LineaRequest(productId, java.util.Arrays.stream(meses).mapToObj(BigDecimal::valueOf).toList());
	}

	private void precio(UUID empresa, UUID producto, String valor) {
		jdbc.update("""
				insert into precio_venta_producto (company_id, product_id, sale_price, created_by)
				values (?, ?, ?, (select user_id from usuario where corporate_email = 'pyc@logistica.test'))""",
				empresa, producto, new BigDecimal(valor));
	}

	private UUID ceco(String empresa, int indice) {
		return jdbc.queryForList("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				where e.code = ? and c.active order by c.code""", UUID.class, empresa).get(indice);
	}

	private UUID empresa(String code) {
		return jdbc.queryForObject("select company_id from empresa where code = ?", UUID.class, code);
	}

	private UUID producto(String sku) {
		return jdbc.queryForObject("select product_id from producto where sku_base = ?", UUID.class, sku);
	}

	private UsuarioPrincipal principal(String correo, String rol) {
		return new UsuarioPrincipal(
				jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo),
				List.of(rol));
	}
}
