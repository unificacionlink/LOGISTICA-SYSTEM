package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.almacen.DespachoService;
import com.tdem.logistica.modules.almacen.dto.CrearNotaRequest;
import com.tdem.logistica.modules.almacen.dto.DespachoItemInput;
import com.tdem.logistica.modules.almacen.dto.NotaInventarioDetalleDto;
import com.tdem.logistica.modules.compras.OrdenDirectaService;
import com.tdem.logistica.modules.compras.dto.CrearOrdenDirectaRequest;
import com.tdem.logistica.modules.repositorio.ArchivadorDocumentos;
import com.tdem.logistica.modules.repositorio.RepositorioService;
import com.tdem.logistica.modules.repositorio.dto.DocumentoRepositorioDto;
import com.tdem.logistica.security.UsuarioPrincipal;
import com.tdem.logistica.storage.StorageClient;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DEC-L091: el ERP archiva el PDF de cada requerimiento aprobado, OC aprobada y
 * Nota de Inventario (emitida y anulada) en repositorio/TIPO/EMPRESA/AAAA-MM, una
 * sola vez, y el repositorio los lista por tipo, empresa y mes y los baja en ZIP.
 */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class RepositorioDocumentosTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private ArchivadorDocumentos archivador;
	@Autowired private RepositorioService repositorio;
	@Autowired private OrdenDirectaService directas;
	@Autowired private DespachoService despacho;
	@MockitoBean private StorageClient storage;

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
	void archivaUnaVezYListaPorTipoEmpresaYMes() throws IOException {
		Map<String, byte[]> guardado = new HashMap<>();
		List<String> claves = new ArrayList<>();
		when(storage.privadoConfigurado()).thenReturn(true);
		when(storage.subirPrivado(anyString(), any(), anyString())).thenAnswer(inv -> {
			guardado.put(inv.getArgument(0), inv.getArgument(1));
			claves.add(inv.getArgument(0));
			return inv.getArgument(0);
		});
		when(storage.leerPrivado(anyString())).thenAnswer(inv -> guardado.getOrDefault(inv.getArgument(0), new byte[0]));

		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		String mes = LocalDate.now(ZoneId.of("America/Lima")).toString().substring(0, 7);
		String req = requerimientoAprobado();
		var oc = directas.crear(new CrearOrdenDirectaRequest(proveedor(), "PEN", "CONTADO", "ALMACEN_CLIENTE", null,
				3, "CALENDARIO", "MAS_IGV", null, null, null,
				List.of(new CrearOrdenDirectaRequest.Linea(null, "Servicio de prueba", BigDecimal.ONE, BigDecimal.TEN)),
				null), logistica);
		NotaInventarioDetalleDto ni = notaEmitida(logistica);

		assertEquals(3, archivador.archivarPendientes(), "requerimiento, OC y nota");
		assertEquals(0, archivador.archivarPendientes(), "no se archiva dos veces");
		assertTrue(claves.contains("repositorio/ORDEN_COMPRA/TDEMSRL/" + mes + "/" + oc.orderCode() + ".pdf"), claves.toString());
		assertTrue(claves.contains("repositorio/REQUERIMIENTO/TDEMSRL/" + mes + "/" + req + ".pdf"), claves.toString());
		byte[] pdf = guardado.get("repositorio/ORDEN_COMPRA/TDEMSRL/" + mes + "/" + oc.orderCode() + ".pdf");
		assertEquals("%PDF", new String(pdf, 0, 4));

		// Anular la nota archiva tambien su version anulada.
		despacho.anularNota(ni.nota().inventoryNoteId(), "Prueba del repositorio", logistica);
		assertEquals(1, archivador.archivarPendientes());
		assertTrue(claves.contains("repositorio/NOTA_INVENTARIO/TDEMSRL/" + mes + "/" + ni.nota().inventoryNoteCode()
				+ "-ANULADA.pdf"), claves.toString());

		List<DocumentoRepositorioDto> todo = repositorio.listar(null, null, mes, null, logistica);
		assertEquals(4, todo.stream().filter(d -> d.descarga() != null && d.descarga().startsWith("/api/repositorio/")).count());
		List<DocumentoRepositorioDto> ocs = repositorio.listar("ORDEN_COMPRA", "TDEMSRL", mes, null, logistica);
		assertEquals(1, ocs.size());
		assertEquals(oc.orderCode(), ocs.get(0).codigo());
		assertEquals(1, repositorio.listar(null, null, mes, req.substring(4, 12), logistica).size(), "busca por código");
		assertTrue(repositorio.listar(null, null, "2020-01", null, logistica).isEmpty());

		UUID id = UUID.fromString(ocs.get(0).descarga().split("/")[3]);
		assertEquals("%PDF", new String(repositorio.descargar(id, logistica).contenido(), 0, 4));

		int entradas = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
				repositorio.zip(null, "TDEMSRL", mes, logistica).contenido()))) {
			for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
				assertTrue(e.getName().contains("/TDEMSRL/" + mes + "/"), e.getName());
				entradas++;
			}
		}
		assertEquals(4, entradas);

		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));
		assertThrows(ApiException.class, () -> repositorio.listar(null, null, null, null, solicitante));
		assertThrows(ApiException.class, () -> repositorio.listar(null, null, "noviembre", null, logistica));
	}

	// ------------------------------------------------------------------

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}

	private UUID proveedor() {
		return jdbc.queryForObject("insert into proveedor (ruc, legal_name) values ('20100000091', 'Proveedor Repositorio') "
				+ "returning supplier_id", UUID.class);
	}

	/** REQ de TDEM aprobado hoy, con su aprobacion decidida. */
	private String requerimientoAprobado() {
		String codigo = "REQ-REPO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status, priority)
				select ?, e.company_id, c.ceco_id, ?, 'INTERNO', 'CONSUMO', 'prueba repositorio', 'EN_ATENCION',
				       'APROVISIONAMIENTO'
				  from empresa e join ceco c on c.company_id = e.company_id and c.code = 'ADM' where e.code = 'TDEMSRL'
				returning request_id""", UUID.class, codigo, usuario("solicitante@logistica.test"));
		jdbc.update("""
				insert into aprobacion (entity_type, entity_id, status, decided_by, decided_at)
				values ('REQUERIMIENTO', ?, 'APROBADO', ?, now())""", req, usuario("aprobador@logistica.test"));
		return codigo;
	}

	/** Nota de inventario emitida de un REQ con stock reservado (no aprobado por flujo: no se archiva como REQ). */
	private NotaInventarioDetalleDto notaEmitida(UsuarioPrincipal logistica) {
		UUID empresa = jdbc.queryForObject("select company_id from empresa where code = 'TDEMSRL'", UUID.class);
		UUID ceco = jdbc.queryForObject("select ceco_id from ceco where company_id = ? and code = 'ADM'", UUID.class, empresa);
		UUID producto = jdbc.queryForObject("select product_id from producto_variante where internal_variant_code = 'AC01005-00'", UUID.class);
		UUID variante = jdbc.queryForObject("select variant_id from producto_variante where internal_variant_code = 'AC01005-00'", UUID.class);
		UUID uom = jdbc.queryForObject("select base_uom_id from producto where product_id = ?", UUID.class, producto);
		UUID almacen = jdbc.queryForObject("select warehouse_id from almacen where is_group_central", UUID.class);
		UUID req = jdbc.queryForObject("""
				insert into requerimiento (request_code, company_id, ceco_id, requester_user_id, requester_type,
				  purpose, justification, status)
				values ('REQ-REPO-NI', ?, ?, ?, 'INTERNO', 'CONSUMO', 'prueba', 'EN_ATENCION') returning request_id""",
				UUID.class, empresa, ceco, usuario("solicitante@logistica.test"));
		UUID item = jdbc.queryForObject("""
				insert into requerimiento_item (request_id, product_id, variant_id, uom_id, qty_requested)
				values (?, ?, ?, ?, 2) returning request_item_id""", UUID.class, req, producto, variante, uom);
		jdbc.update("""
				insert into movimiento_inventario (company_id, warehouse_id, product_id, variant_id, movement_type,
				  qty, reference_type, reference_id, created_by)
				values (?, ?, ?, ?, 'AJUSTE', 2, 'AJUSTE', gen_random_uuid(), ?)""",
				empresa, almacen, producto, variante, usuario("logistica@logistica.test"));
		jdbc.update("""
				insert into reserva_stock (request_item_id, warehouse_id, product_id, variant_id, qty_reserved, status,
				  reserved_at, origin)
				values (?, ?, ?, ?, 2, 'RESERVADA', now(), 'VERIFICACION_STOCK')""", item, almacen, producto, variante);
		return despacho.crearNotaInventario(req,
				new CrearNotaRequest(List.of(new DespachoItemInput(item, new BigDecimal("2"))), 1, null, null), logistica);
	}
}
