package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.dto.CrearNotaRequest;
import com.tdem.logistica.modules.almacen.dto.DespachoItemInput;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.identidad.AdminService;
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

/**
 * Ajustes de la marcha blanca: suplente del aprobador por CECO (DEC-L087) y
 * anulacion de la Nota de Inventario con motivo (DEC-L088).
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class SuplenteYAnulacionTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private AdminService admin;
	@Autowired private AprobacionService aprobaciones;
	@Autowired private DespachoService despacho;

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
	void elSuplenteDecideLosRequerimientosDelCeco() {
		UsuarioPrincipal adm = new UsuarioPrincipal(usuario("admin@logistica.test"), List.of("ADMIN"));
		UUID aprobador = usuario("aprobador@logistica.test");
		UUID suplente = aprobadorNuevo("suplente@logistica.test");
		UUID otro = aprobadorNuevo("otro.aprobador@logistica.test");
		UUID ceco = ceco("SUP-1");
		admin.asignarCecosAprobados(aprobador, List.of(ceco), adm);
		admin.asignarCecosSuplente(suplente, List.of(ceco), adm);
		assertEquals(suplente, jdbc.queryForObject("select backup_approver_user_id from ceco where ceco_id = ?",
				UUID.class, ceco));
		assertThrows(ApiException.class, () -> admin.asignarCecosSuplente(aprobador, List.of(ceco), adm),
				"el aprobador no puede ser su propio suplente");

		UUID aprobacion = requerimientoPendiente(ceco, aprobador);
		UsuarioPrincipal comoSuplente = new UsuarioPrincipal(suplente, List.of("APROBADOR"));
		UsuarioPrincipal comoOtro = new UsuarioPrincipal(otro, List.of("APROBADOR"));
		assertTrue(aprobaciones.listarPendientes(comoSuplente).stream().anyMatch(a -> a.approvalId().equals(aprobacion)),
				"el suplente lo ve en su bandeja de aprobaciones");
		assertTrue(aprobaciones.listarPendientes(comoOtro).stream().noneMatch(a -> a.approvalId().equals(aprobacion)),
				"otro aprobador no");
		assertThrows(ApiException.class, () -> aprobaciones.decidir(aprobacion,
				new DecidirAprobacionRequest("APROBADO", null, null), comoOtro));

		aprobaciones.decidir(aprobacion, new DecidirAprobacionRequest("APROBADO", null, null), comoSuplente);
		assertEquals("APROBADO", jdbc.queryForObject("select status from aprobacion where approval_id = ?",
				String.class, aprobacion));
		assertEquals(1, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'CECO' and entidad_id = ? and accion = 'ASIGNAR_SUPLENTE'""",
				Integer.class, ceco));
	}

	@Test
	void anularNotaDevuelveElStockYLaReserva() {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		Despachable d = requerimientoConStock("AC01004-00", 5);

		NotaInventarioDetalleDto ni = despacho.crearNotaInventario(d.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(d.itemId, new BigDecimal("3"))), 1, null, null), logistica);
		UUID nota = ni.nota().inventoryNoteId();
		assertEquals(0, stock("AC01004-00").compareTo(new BigDecimal("2")));
		assertEquals(0, reservado(d.itemId).compareTo(new BigDecimal("2")));

		assertThrows(ApiException.class, () -> despacho.anularNota(nota, "  ", logistica), "sin motivo");
		assertThrows(ApiException.class, () -> despacho.anularNota(nota, "Error de cantidad",
				new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"))));

		NotaInventarioDetalleDto anulada = despacho.anularNota(nota, "Se despachó la talla equivocada", logistica);
		assertEquals("ANULADA", anulada.nota().status());
		assertEquals("Se despachó la talla equivocada", anulada.nota().annulmentReason());
		assertNotNull(anulada.nota().annulledAt());
		assertEquals(0, stock("AC01004-00").compareTo(new BigDecimal("5")), "lo despachado vuelve al stock");
		assertEquals(0, reservado(d.itemId).compareTo(new BigDecimal("5")), "y vuelve a quedar reservado para el REQ");
		assertEquals("ANULADA", jdbc.queryForObject("select status from salida where issue_id = "
				+ "(select issue_id from nota_inventario where inventory_note_id = ?)", String.class, nota));
		assertThrows(ApiException.class, () -> despacho.anularNota(nota, "Otra vez", logistica), "ya anulada");

		// Se puede volver a despachar todo lo reservado.
		NotaInventarioDetalleDto otra = despacho.crearNotaInventario(d.requestId,
				new CrearNotaRequest(List.of(new DespachoItemInput(d.itemId, new BigDecimal("5"))), 1, null, null), logistica);
		assertEquals(0, stock("AC01004-00").signum());

		// Con envio registrado lo despachado ya salio: no se anula.
		jdbc.update("update nota_inventario set status = 'DESPACHADA' where inventory_note_id = ?", otra.nota().inventoryNoteId());
		assertThrows(ApiException.class, () -> despacho.anularNota(otra.nota().inventoryNoteId(), "Tarde", logistica));
	}

	// ------------------------------------------------------------------

	private record Despachable(UUID requestId, UUID itemId) {
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}

	private UUID aprobadorNuevo(String correo) {
		UUID id = jdbc.queryForObject("""
				insert into usuario (company_id, corporate_email, display_name, active, password_hash)
				select company_id, ?, ?, true, 'x' from empresa where code = 'TDEMSRL' returning user_id""",
				UUID.class, correo, correo);
		jdbc.update("""
				insert into usuario_rol (user_id, role_id, company_id)
				select ?, r.role_id, e.company_id from rol r, empresa e where r.code = 'APROBADOR' and e.code = 'TDEMSRL'""", id);
		return id;
	}

	private UUID ceco(String code) {
		return jdbc.queryForObject("""
				insert into ceco (company_id, code, name) select company_id, ?, 'Proyecto ' || ? from empresa where code = 'GOYDELSAC'
				returning ceco_id""", UUID.class, code, code);
	}

	private UUID requerimientoPendiente(UUID ceco, UUID aprobador) {
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				select ?, c.company_id, c.ceco_id, ?, 'INTERNO', 'CONSUMO', 'prueba suplente', 'PENDIENTE_APROBACION',
				       'APROVISIONAMIENTO' from ceco c where c.ceco_id = ? returning request_id""",
				UUID.class, "REQ-SUP-" + UUID.randomUUID().toString().substring(0, 6), usuario("solicitante@logistica.test"), ceco);
		return jdbc.queryForObject("""
				insert into aprobacion (entity_type, entity_id, assigned_to) values ('REQUERIMIENTO', ?, ?) returning approval_id""",
				UUID.class, req, aprobador);
	}

	private Despachable requerimientoConStock(String variante, int qty) {
		UUID logisticaId = usuario("logistica@logistica.test");
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID cecoId = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID producto = jdbc.queryForObject("select product_id from producto_variante where internal_variant_code = ?",
				UUID.class, variante);
		UUID varianteId = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = ?",
				UUID.class, variante);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		UUID almacen = jdbc.queryForObject("select warehouse_id from almacen where is_group_central", UUID.class);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values (?, ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba anulacion', 'EN_ATENCION') returning request_id""",
				UUID.class, "REQ-ANU-" + UUID.randomUUID().toString().substring(0, 6), empresa, cecoId,
				usuario("solicitante@logistica.test"));
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, ?) returning request_item_id""", UUID.class, req, producto, varianteId, uom, qty);
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				select a.company_id, a.warehouse_id, ?, ?, 'AJUSTE', ?, 'AJUSTE', gen_random_uuid(), ?
				  from almacen a where a.warehouse_id = ?""", producto, varianteId, qty, logisticaId, almacen);
		jdbc.update("""
				insert into reserva_stock (request_item_id, warehouse_id, product_id, variant_id, qty_reserved, status,
				  reserved_at, origin)
				values (?, ?, ?, ?, ?, 'RESERVADA', now(), 'VERIFICACION_STOCK')""", item, almacen, producto, varianteId, qty);
		return new Despachable(req, item);
	}

	private BigDecimal stock(String variante) {
		return jdbc.queryForObject("""
				select coalesce(sum(m.qty), 0) from movimiento_inventario m join producto_variante v on v.variant_id = m.variant_id
				 where v.internal_variant_code = ?""", BigDecimal.class, variante);
	}

	private BigDecimal reservado(UUID requestItemId) {
		return jdbc.queryForObject("select coalesce(sum(qty_reserved), 0) from reserva_stock where request_item_id = ? "
				+ "and status = 'RESERVADA'", BigDecimal.class, requestItemId);
	}
}
