package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.presupuestos.ControlPresupuestalService;
import com.tdem.logistica.modules.presupuestos.PresupuestoService;
import com.tdem.logistica.modules.presupuestos.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.requerimientos.dto.CrearRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.ItemRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 15 (DEC-L038): control presupuestal al enviar el REQ, excepciones y cumplimiento. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ControlPresupuestalTest {

	private static final LocalDate FECHA = LocalDate.of(2027, 3, 15); // 2027-S1

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private PresupuestoService presupuestos;
	@Autowired private RequerimientoService requerimientos;

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
	void controlExcepcionesYCumplimiento() {
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UsuarioPrincipal pyc = principal("pyc@logistica.test", "PYC");
		UsuarioPrincipal aprobador = principal("aprobador@logistica.test", "APROBADOR");
		UUID ceco = jdbc.queryForList("""
				select c.ceco_id from ceco c join empresa e on e.company_id = c.company_id
				where e.code = 'TDEMSRL' and c.active order by c.code""", UUID.class).getFirst();
		UUID tdem = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		jdbc.update("update ceco set approver_user_id = ? where ceco_id = ?", aprobador.userId(), ceco);
		precio(tdem, "AC01001", "15");
		precio(tdem, "AC01003", "40");

		// --- Sin periodo, o con el control desactivado, el envio no se valida ---
		RequerimientoDto r1 = enviar(ceco, Map.of("AC01001", 1), null, solicitante);
		assertNull(requerimientos.obtener(r1.id(), solicitante).controlPresupuestal());
		PeriodoDto periodo = presupuestos.crearPeriodo(new CrearPeriodoRequest(ceco, 2027, 1), pyc);
		assertFalse(periodo.controlObligatorio());
		RequerimientoDto r2 = enviar(ceco, Map.of("AC01001", 1), null, solicitante);
		assertNull(requerimientos.obtener(r2.id(), solicitante).controlPresupuestal());

		// --- Solo PYC activa el control ---
		assertThrows(ApiException.class, () -> presupuestos.controlPeriodo(periodo.periodId(), true, solicitante));
		assertTrue(presupuestos.controlPeriodo(periodo.periodId(), true, pyc).controlObligatorio());

		// --- Sin presupuesto aprobado: excepcion que exige justificacion ---
		ApiException sinJustificar = assertThrows(ApiException.class,
				() -> enviar(ceco, Map.of("AC01001", 5), null, solicitante));
		assertEquals(ControlPresupuestalService.CODIGO_EXCEPCION, sinJustificar.getCode());
		RequerimientoDto r3 = enviar(ceco, Map.of("AC01001", 5), "Urgencia de obra", solicitante);
		ControlPresupuestalDto c3 = requerimientos.obtener(r3.id(), aprobador).controlPresupuestal();
		assertEquals("EXCEPCION_JUSTIFICADA", c3.estado());
		assertEquals("SIN_PRESUPUESTO_APROBADO", c3.desviaciones().getFirst().motivo());
		assertEquals("Urgencia de obra", c3.justificacion());

		// --- Presupuesto aprobado: 20 de AC01001; ya se pidieron 7 (1 + 1 + 5) ---
		PresupuestoDetalleDto b = presupuestos.crear(new CrearPresupuestoRequest(periodo.periodId(), null), pyc);
		UUID id = b.id();
		presupuestos.guardarLineas(id, List.of(new LineaRequest(producto("AC01001"), meses(10, 10))), pyc);
		presupuestos.emitir(id, pyc);
		presupuestos.decidir(id, "APROBADO", null, aprobador);

		RequerimientoDto r4 = enviar(ceco, Map.of("AC01001", 13), null, solicitante);
		ControlPresupuestalDto c4 = requerimientos.obtener(r4.id(), solicitante).controlPresupuestal();
		assertEquals("CUBIERTO", c4.estado());
		assertEquals(0, new BigDecimal("195.00").compareTo(c4.monto()), "13 x 15");
		assertEquals(b.codigo(), c4.presupuestoCodigo());

		// --- Exceso y SKU no presupuestado en el mismo envio ---
		ApiException exceso = assertThrows(ApiException.class,
				() -> enviar(ceco, Map.of("AC01001", 1, "AC01003", 2), null, solicitante));
		assertTrue(exceso.getMessage().contains("AC01001 excede en 1"), exceso.getMessage());
		assertTrue(exceso.getMessage().contains("AC01003 no presupuestado"), exceso.getMessage());
		enviar(ceco, Map.of("AC01001", 1, "AC01003", 2), "Cambio de alcance", solicitante);

		// --- Excepciones visibles para PYC en el presupuesto, no para el solicitante ---
		assertEquals(2, presupuestos.obtener(id, pyc).excepciones().size());
		assertTrue(presupuestos.obtener(id, solicitante).excepciones().isEmpty());

		// --- Cumplimiento: 4 recepcionados en destino del REQ r4 ---
		recibirEnDestino(r4.id(), "AC01001", 4);
		CumplimientoDto cum = presupuestos.cumplimiento(id, pyc);
		assertTrue(cum.conCompra());
		assertTrue(cum.puedeDescargar());
		CumplimientoFilaDto ac1 = fila(cum, "AC01001");
		// Pedido 1 + 1 + 5 + 13 + 1 = 21; real 4.
		assertEquals(0, new BigDecimal("20").compareTo(ac1.programado()));
		assertEquals(0, new BigDecimal("4").compareTo(ac1.real()));
		assertEquals(0, new BigDecimal("17").compareTo(ac1.pendiente()));
		assertEquals(0, new BigDecimal("16").compareTo(ac1.saldo()));
		assertEquals(0, new BigDecimal("-1").compareTo(ac1.disponible()));
		assertEquals(0, new BigDecimal("300.00").compareTo(ac1.venta().getFirst()));
		assertNull(ac1.precioCompra(), "sin OC registrada no se inventa precio de compra");
		CumplimientoFilaDto ac3 = fila(cum, "AC01003");
		assertTrue(ac3.sinPresupuesto());
		assertEquals(0, BigDecimal.ZERO.compareTo(ac3.venta().getFirst()));
		assertNull(ac3.venta().get(1));
		ResumenFamiliaDto total = cum.resumen().getLast();
		assertEquals("TOTAL", total.familia());
		assertEquals(0, new BigDecimal("300.00").compareTo(total.venta().getFirst()));
		assertNull(total.venta().get(1), "una fila sin precio deja el total INCOMPLETO");

		// --- Excel exclusivo de PYC; el solicitante no ve costos ---
		byte[] excel = presupuestos.cumplimientoExcel(id, pyc).contenido();
		assertEquals('P', excel[0]);
		assertEquals('K', excel[1]);
		CumplimientoDto vistaSolicitante = presupuestos.cumplimiento(id, solicitante);
		assertFalse(vistaSolicitante.conCompra());
		assertNull(fila(vistaSolicitante, "AC01001").compra());
		assertThrows(ApiException.class, () -> presupuestos.cumplimientoExcel(id, solicitante));

		// --- En revision sigue rigiendo la version aprobada (no queda "sin presupuesto") ---
		presupuestos.modificar(id, pyc);
		ApiException enRevision = assertThrows(ApiException.class,
				() -> enviar(ceco, Map.of("AC01001", 1), null, solicitante));
		assertTrue(enRevision.getMessage().contains("excede"), enRevision.getMessage());
	}

	// ------------------------------------------------------------------

	private RequerimientoDto enviar(UUID ceco, Map<String, Integer> items, String justificacion, UsuarioPrincipal p) {
		List<ItemRequerimientoRequest> lineas = items.entrySet().stream().map(e -> new ItemRequerimientoRequest(
				jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?",
						UUID.class, e.getKey() + "-00"),
				BigDecimal.valueOf(e.getValue()), null, null)).toList();
		RequerimientoDto r = requerimientos.crear(new CrearRequerimientoRequest(ceco, "NORMAL", "CONSUMO",
				"Materiales para la obra del semestre", FECHA, lineas), p);
		return requerimientos.enviar(r.id(), justificacion, p);
	}

	/** Simula un despacho recepcionado en destino (lo que cuenta como consumo real). */
	private void recibirEnDestino(UUID requestId, String sku, int qty) {
		UUID issue = jdbc.queryForObject("""
				insert into salida (company_id, warehouse_id, issue_type, ceco_id, request_id, created_by)
				select r.company_id, (select warehouse_id from almacen limit 1), 'CONSUMO_INTERNO', r.ceco_id,
				       r.request_id, r.requester_user_id
				from requerimiento r where r.request_id = ? returning issue_id""", UUID.class, requestId);
		jdbc.update("""
				insert into salida_item (issue_id, product_id, qty, request_item_id)
				select ?, ri.product_id, ?, ri.request_item_id from requerimiento_item ri
				join producto p on p.product_id = ri.product_id where ri.request_id = ? and p.sku_base = ?""",
				issue, qty, requestId, sku);
		UUID nota = jdbc.queryForObject("""
				insert into nota_inventario (note_number, inventory_note_code, company_id, request_id, issue_id,
				  origin_warehouse_id, movement_type, picking_user_id, created_by)
				select 1, 'NI-PRUEBA', r.company_id, r.request_id, s.issue_id, s.warehouse_id, 'CONSUMO',
				       r.requester_user_id, r.requester_user_id
				from requerimiento r join salida s on s.issue_id = ? where r.request_id = ?
				returning inventory_note_id""", UUID.class, issue, requestId);
		jdbc.update("""
				insert into envio (shipment_type, request_id, issue_id, inventory_note_id, delivery_mode, status, created_by)
				select 'DESPACHO_REQUERIMIENTO', request_id, ?, ?, 'PROPIO', 'RECEPCIONADO', requester_user_id
				from requerimiento where request_id = ?""", issue, nota, requestId);
	}

	private static CumplimientoFilaDto fila(CumplimientoDto c, String sku) {
		return c.filas().stream().filter(f -> f.sku().equals(sku)).findFirst().orElseThrow();
	}

	private static List<BigDecimal> meses(int... q) {
		BigDecimal[] m = new BigDecimal[6];
		for (int i = 0; i < 6; i++) {
			m[i] = BigDecimal.valueOf(i < q.length ? q[i] : 0);
		}
		return List.of(m);
	}

	private void precio(UUID empresa, String sku, String valor) {
		jdbc.update("""
				insert into precio_venta_producto (company_id, product_id, sale_price, created_by)
				values (?, ?, ?, (select user_id from usuario where corporate_email = 'pyc@logistica.test'))""",
				empresa, producto(sku), new BigDecimal(valor));
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
