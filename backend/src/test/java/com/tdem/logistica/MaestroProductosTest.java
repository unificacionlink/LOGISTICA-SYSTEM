package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.maestro.MaestroService;
import com.tdem.logistica.modules.maestro.dto.*;
import com.tdem.logistica.modules.requerimientos.RequerimientoService;
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
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 8b (DEC-L025): alta de SKU por Logistica y solicitudes de alta (RF-004). */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class MaestroProductosTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private MaestroService maestro;
	@Autowired private RequerimientoService requerimientoService;

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
	void altaDeSkuYSolicitudesDeAlta() {
		UsuarioPrincipal logistica = new UsuarioPrincipal(usuario("logistica@logistica.test"), List.of("LOGISTICA"));
		UsuarioPrincipal solicitante = new UsuarioPrincipal(usuario("solicitante@logistica.test"), List.of("SOLICITANTE"));

		ClasificacionDto c = maestro.clasificacion();
		ClasificacionDto.Familia ac01 = c.tipos().stream().filter(t -> t.code().equals("AC")).findFirst().orElseThrow()
				.familias().stream().filter(f -> f.code().equals("01")).findFirst().orElseThrow();
		ClasificacionDto.Familia ep01 = c.tipos().stream().filter(t -> t.code().equals("EP")).findFirst().orElseThrow()
				.familias().stream().filter(f -> f.code().equals("01")).findFirst().orElseThrow();
		UUID und = c.unidades().stream().filter(u -> u.code().equals("UND")).findFirst().orElseThrow().id();
		assertTrue(c.unidades().stream().noneMatch(u -> u.code().equals("ND")), "ND no se ofrece para altas");

		// --- Alta directa por Logistica: correlativo siguiente de la familia ---
		int ultimo = jdbc.queryForObject(
				"select max(substring(sku_base from 5)::int) from producto where sku_base ~ '^AC01[0-9]{3}$'", Integer.class);
		String esperado = "AC01" + String.format("%03d", ultimo + 1);
		ProductoCreadoDto p = maestro.crearProducto(new CrearProductoRequest(ac01.id(), "  cinta   aislante 3m  ", und, null), logistica);
		assertEquals(esperado, p.skuBase());
		assertEquals("CINTA AISLANTE 3M", p.nombre());
		assertEquals(List.of(esperado + "-00"), p.variantes());

		assertThrows(ApiException.class, () -> maestro.crearProducto(
				new CrearProductoRequest(ac01.id(), "Cinta aislante 3M", und, null), logistica), "duplicado en la familia");
		assertThrows(ApiException.class, () -> maestro.crearProducto(
				new CrearProductoRequest(ac01.id(), "Otro", und, null), solicitante), "solo Logistica");

		// --- Con tallas ---
		ProductoCreadoDto casco = maestro.crearProducto(new CrearProductoRequest(ep01.id(), "Barbiquejo nuevo", und,
				List.of(new VarianteInput("TALLA", "s"), new VarianteInput("talla", "M"))), logistica);
		assertEquals(List.of(casco.skuBase() + "-M", casco.skuBase() + "-S"), casco.variantes());
		assertThrows(ApiException.class, () -> maestro.crearProducto(new CrearProductoRequest(ep01.id(), "Mezcla", und,
				List.of(new VarianteInput("TALLA", "S"), new VarianteInput("COLOR", "ROJO"))), logistica));
		assertEquals(3, maestro.agregarVariante(casco.productId(), new VarianteInput("TALLA", "L"), logistica).variantes().size());
		assertThrows(ApiException.class, () -> maestro.agregarVariante(casco.productId(), new VarianteInput("TALLA", "L"), logistica));
		assertThrows(ApiException.class, () -> maestro.agregarVariante(p.productId(), new VarianteInput("TALLA", "L"), logistica),
				"producto sin atributos");

		// El solicitante ya lo ve en el buscador de requerimientos.
		assertTrue(catalogo(solicitante).contains(esperado + "-00"));

		// --- Solicitud de alta (RF-004) ---
		SolicitudProductoDto s = maestro.crearSolicitud(new CrearSolicitudRequest(ac01.id(), "Pinza amperimetrica digital",
				und, null, null, "Para medicion en campo", "Mantenimiento de tableros"), solicitante);
		assertEquals("PENDIENTE_APROBACION_LOGISTICA", s.estado());
		assertTrue(s.codigo().startsWith("SMP-"));
		assertEquals(1, maestro.listarSolicitudes(solicitante).size());
		assertThrows(ApiException.class, () -> maestro.aprobarSolicitud(s.id(),
				new AprobarSolicitudRequest(ac01.id(), "X", und), solicitante));

		SolicitudProductoDto aprobada = maestro.aprobarSolicitud(s.id(),
				new AprobarSolicitudRequest(ac01.id(), "Pinza amperimetrica digital 600A", und), logistica);
		assertEquals("APROBADO", aprobada.estado());
		assertEquals("AC01" + String.format("%03d", ultimo + 2), aprobada.skuGenerado());
		assertEquals(s.id(), jdbc.queryForObject(
				"select approved_request_id from producto where sku_base = ?", UUID.class, aprobada.skuGenerado()));
		assertThrows(ApiException.class, () -> maestro.aprobarSolicitud(s.id(),
				new AprobarSolicitudRequest(ac01.id(), "Otra vez", und), logistica), "ya revisada");

		// Posible duplicado y rechazo con motivo.
		SolicitudProductoDto dup = maestro.crearSolicitud(new CrearSolicitudRequest(ac01.id(), "Cinta aislante 3M",
				und, null, null, null, "Stock de obra"), solicitante);
		assertNotNull(dup.posibleDuplicado(), "advierte que ya existe " + esperado);
		SolicitudProductoDto rechazada = maestro.rechazarSolicitud(dup.id(), "Ya existe como " + esperado, logistica);
		assertEquals("RECHAZADO", rechazada.estado());
		assertEquals("Ya existe como " + esperado, rechazada.comentarioRevision());

		// --- Inactivar: sale del buscador de requerimientos ---
		maestro.editarProducto(p.productId(), null, null, false, logistica);
		assertFalse(catalogo(solicitante).contains(esperado + "-00"));
	}

	private List<String> catalogo(UsuarioPrincipal p) {
		return requerimientoService.obtenerDatosFormulario(p).variantes().stream()
				.map(v -> v.codigoVariante()).toList();
	}

	private UUID usuario(String correo) {
		return jdbc.queryForObject("select user_id from usuario where corporate_email = ?", UUID.class, correo);
	}
}
