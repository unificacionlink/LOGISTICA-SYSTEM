package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.aprobaciones.AprobacionService;
import com.tdem.logistica.modules.aprobaciones.dto.DecidirAprobacionRequest;
import com.tdem.logistica.modules.compras.ComprasService;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
import com.tdem.logistica.modules.requerimientos.dto.CrearRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.ItemRequerimientoRequest;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDetalleDto;
import com.tdem.logistica.modules.requerimientos.dto.RequerimientoDto;
import com.tdem.logistica.modules.tracking.TrackingService;
import com.tdem.logistica.modules.tracking.dto.EventoDto;
import com.tdem.logistica.modules.tracking.dto.TrackingDto;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 10 (DEC-L027): observar REQ, programar entrega y motivo en acciones irreversibles. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class ObservarProgramarTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private RequerimientoService requerimientos;
	@Autowired private AprobacionService aprobaciones;
	@Autowired private ComprasService compras;
	@Autowired private TrackingService tracking;

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
	void observarCorregirReenviarYProgramarEntrega() {
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		UsuarioPrincipal aprobador = new UsuarioPrincipal(usuario("aprobador@logistica.test"), List.of("APROBADOR"));
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));

		RequerimientoDto req = requerimientos.crear(solicitud(2), solicitante);
		requerimientos.enviar(req.id(), solicitante);
		UUID aprobacion1 = requerimientos.obtener(req.id(), aprobador).pendingApprovalId();
		assertNotNull(aprobacion1);

		// --- Programar entrega antes de aprobar: no ---
		assertThrows(ApiException.class, () -> requerimientos.programarEntrega(req.id(), hoy, logistica));

		// --- Observar exige motivo (RF-006) ---
		assertThrows(ApiException.class, () -> aprobaciones.decidir(aprobacion1,
				new DecidirAprobacionRequest("OBSERVADO", null, "  "), aprobador));
		RequerimientoDto observado = aprobaciones.decidir(aprobacion1,
				new DecidirAprobacionRequest("OBSERVADO", null, " Falta sustento del consumo "), aprobador);
		assertEquals("OBSERVADO", observado.status());

		// --- El solicitante ve el motivo (RF-048) y no hay aprobacion pendiente ---
		RequerimientoDetalleDto detalle = requerimientos.obtener(req.id(), solicitante);
		assertNull(detalle.pendingApprovalId());
		assertNotNull(detalle.observacion());
		assertEquals("Falta sustento del consumo", detalle.observacion().motivo());
		assertNotNull(detalle.observacion().observadoPor());

		// --- Corrige (cambia la cantidad) y reenvia: nueva aprobacion pendiente ---
		requerimientos.actualizar(req.id(), solicitud(5), solicitante);
		assertEquals(0, new BigDecimal("5").compareTo(
				requerimientos.obtener(req.id(), solicitante).items().get(0).cantidad()));
		requerimientos.enviar(req.id(), solicitante);
		RequerimientoDetalleDto reenviado = requerimientos.obtener(req.id(), aprobador);
		assertEquals("PENDIENTE_APROBACION", reenviado.requerimiento().status());
		assertNull(reenviado.observacion(), "el motivo solo se muestra mientras esta observado");
		UUID aprobacion2 = reenviado.pendingApprovalId();
		assertNotNull(aprobacion2);
		assertNotEquals(aprobacion1, aprobacion2);
		assertThrows(ApiException.class, () -> requerimientos.actualizar(req.id(), solicitud(1), solicitante),
				"pendiente de aprobacion ya no se edita");

		assertEquals("EN_ATENCION", aprobaciones.decidir(aprobacion2,
				new DecidirAprobacionRequest("APROBADO", null, null), aprobador).status());

		// --- Programar entrega (RF-068): solo Logistica, sin fechas pasadas ---
		LocalDate fecha = hoy.plusDays(3);
		assertThrows(ApiException.class, () -> requerimientos.programarEntrega(req.id(), fecha, solicitante));
		assertThrows(ApiException.class, () -> requerimientos.programarEntrega(req.id(), hoy.minusDays(1), logistica));
		RequerimientoDto programado = requerimientos.programarEntrega(req.id(), fecha, logistica);
		assertEquals(fecha, programado.scheduledDeliveryDate());
		assertEquals(hoy.plusDays(10), programado.requiredDate(), "la fecha requerida no se toca");
		requerimientos.programarEntrega(req.id(), fecha, logistica); // misma fecha: no se audita de nuevo
		requerimientos.programarEntrega(req.id(), fecha.plusDays(1), logistica);
		assertEquals(2, jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'REQUERIMIENTO' and entidad_id = ?
				  and accion = 'PROGRAMAR_ENTREGA'""", Integer.class, req.id()));

		// --- Anular OC y cerrar RFQ exigen motivo (RF-064) ---
		UUID[] rfqYOrden = crearRfqYOrden(req.id(), logistica.userId());
		assertThrows(ApiException.class, () -> compras.cerrarRfq(rfqYOrden[0], null, logistica));
		assertEquals("CERRADA", compras.cerrarRfq(rfqYOrden[0], "Proveedor sin stock", logistica).status());
		assertThrows(ApiException.class, () -> compras.anularOrden(rfqYOrden[1], " ", logistica));
		assertEquals("ANULADA", compras.anularOrden(rfqYOrden[1], "Precio mal digitado", logistica).status());
		assertEquals("Precio mal digitado", jdbc.queryForObject("""
				select despues ->> 'motivo' from log_cambios where entidad = 'ORDEN_ABASTECIMIENTO'
				  and entidad_id = ? and accion = 'ANULAR'""", String.class, rfqYOrden[1]));

		// --- Tracking reconstruye todo ---
		TrackingDto t = tracking.obtener(req.id(), solicitante);
		assertEquals(fecha.plusDays(1), t.entregaProgramada());
		List<String> acciones = t.eventos().stream().map(EventoDto::accion).toList();
		assertTrue(acciones.contains("Observado: Falta sustento del consumo"), acciones.toString());
		assertTrue(acciones.contains("Reenviado a aprobación"), acciones.toString());
		assertTrue(acciones.stream().filter(a -> a.startsWith("Entrega programada para el ")).count() == 2,
				acciones.toString());
		assertTrue(t.eventos().stream().anyMatch(e -> "OC anulada: Precio mal digitado".equals(e.accion())
				&& "OC-TEST-L10".equals(e.referencia())), acciones.toString());
	}

	// ------------------------------------------------------------------

	private CrearRequerimientoRequest solicitud(int cantidad) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID variante = jdbc.queryForObject(
				"select variant_id from producto_variante where internal_variant_code = 'AC01001-00'", UUID.class);
		return new CrearRequerimientoRequest(ceco, "NORMAL", "CONSUMO", "Reposicion de materiales de oficina",
				LocalDate.now(ZoneId.of("America/Lima")).plusDays(10),
				List.of(new ItemRequerimientoRequest(variante, new BigDecimal(cantidad), null, null)));
	}

	/** RFQ ENVIADA + OC EMITIDA minimas sobre el REQ, sin recepciones. */
	private UUID[] crearRfqYOrden(UUID req, UUID logisticaId) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID proveedor = jdbc.queryForObject("""
				insert into proveedor (ruc, legal_name) values ('20111222333', 'Proveedor Lote 10 SAC')
				returning supplier_id""", UUID.class);
		UUID rfq = jdbc.queryForObject("""
				insert into rfq (rfq_code, company_id, request_id, supplier_id, status, created_by)
				values ('RFQ-TEST-L10', ?, ?, ?, 'ENVIADA', ?) returning rfq_id""",
				UUID.class, empresa, req, proveedor, logisticaId);
		UUID cotizacion = jdbc.queryForObject("""
				insert into cotizacion (rfq_id, supplier_id, currency_code, subtotal, total, status)
				values (?, ?, 'PEN', 10, 10, 'SELECCIONADA') returning quotation_id""", UUID.class, rfq, proveedor);
		UUID orden = jdbc.queryForObject("""
				insert into orden_abastecimiento (order_code, company_id, supplier_id, quotation_id, payment_condition,
				  delivery_mode, currency_code, subtotal, total, status, created_by)
				values ('OC-TEST-L10', ?, ?, ?, 'CONTADO', 'ALMACEN_CLIENTE', 'PEN', 10, 10, 'EMITIDA', ?)
				returning order_id""", UUID.class, empresa, proveedor, cotizacion, logisticaId);
		UUID item = jdbc.queryForObject(
				"select request_item_id from requerimiento_item where request_id = ?", UUID.class, req);
		jdbc.update("""
				insert into orden_item (order_id, request_item_id, product_id, variant_id, description, uom_id,
				  qty_ordered, unit_cost, line_subtotal, line_total)
				select ?, ri.request_item_id, ri.product_id, ri.variant_id, 'Producto', ri.uom_id, 1, 10, 10, 10
				from requerimiento_item ri where ri.request_item_id = ?""", orden, item);
		return new UUID[] {rfq, orden};
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
