package com.tdem.logistica;

import com.tdem.logistica.common.ApiException;
import com.tdem.logistica.modules.maestro.PrecioPlazoService;
import com.tdem.logistica.modules.maestro.dto.PreciosVentaDto;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lote 13 (DEC-L035): precio de venta por empresa y plazo de atencion por SKU. */
@SpringBootTest
@ActiveProfiles("seed-test-data")
class PreciosPlazosTest {

	private static EmbeddedPostgres pg;

	@Autowired private JdbcTemplate jdbc;
	@Autowired private PrecioPlazoService service;
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
	void precioDeVentaPorEmpresaConHistorialYAmbito() {
		UsuarioPrincipal pyc = principal("pyc@logistica.test", "PYC");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UUID tdem = empresa("TDEMSRL");
		UUID goydel = empresa("GOYDELSAC");
		UUID producto = producto("AC01001");

		// --- PYC ve solo su empresa; el solicitante no ve precios ---
		PreciosVentaDto inicial = service.listarPrecios(null, pyc);
		assertEquals(tdem, inicial.companyId());
		assertEquals(List.of("TDEMSRL"), inicial.empresas().stream().map(e -> e.code()).toList());
		assertThrows(ApiException.class, () -> service.listarPrecios(null, solicitante));
		assertThrows(ApiException.class, () -> service.registrarPrecio(producto, tdem, BigDecimal.TEN, solicitante));

		// --- Gerencia y Admin ven los precios pero no los registran (no conocen los costos) ---
		UsuarioPrincipal gerencia = principal("gerencia@logistica.test", "GERENCIA");
		UsuarioPrincipal admin = principal("admin@logistica.test", "ADMIN");
		assertEquals(tdem, service.listarPrecios(null, gerencia).companyId());
		assertEquals(3, service.listarPrecios(null, admin).empresas().size());
		assertThrows(ApiException.class, () -> service.registrarPrecio(producto, tdem, BigDecimal.TEN, gerencia));
		assertThrows(ApiException.class, () -> service.registrarPrecio(producto, tdem, BigDecimal.TEN, admin));

		// --- Registrar y corregir: un solo vigente, el anterior queda de historial ---
		assertEquals(0, new BigDecimal("15").compareTo(service.registrarPrecio(producto, tdem, new BigDecimal("15"), pyc).precio()));
		service.registrarPrecio(producto, tdem, new BigDecimal("18.50"), pyc);
		service.registrarPrecio(producto, tdem, new BigDecimal("18.5"), pyc); // mismo precio: sin cambios
		assertEquals(1, filas(tdem, producto, true));
		assertEquals(1, filas(tdem, producto, false));
		assertEquals(0, new BigDecimal("18.5").compareTo(service.listarPrecios(tdem, pyc).precios().getFirst().precio()));
		assertEquals(2, auditorias(producto, "PRECIO_VENTA"));

		// --- Validaciones: precio positivo, empresa del ambito, SKU activo ---
		assertThrows(ApiException.class, () -> service.registrarPrecio(producto, tdem, BigDecimal.ZERO, pyc));
		assertThrows(ApiException.class, () -> service.registrarPrecio(producto, goydel, BigDecimal.TEN, pyc));
		assertThrows(ApiException.class, () -> service.listarPrecios(goydel, pyc));
		UUID inactivo = producto("AC01002");
		jdbc.update("update producto set active = false where product_id = ?", inactivo);
		assertThrows(ApiException.class, () -> service.registrarPrecio(inactivo, tdem, BigDecimal.TEN, pyc));

		// --- Logistica opera todas las empresas; el precio es independiente por empresa ---
		assertEquals(3, service.listarPrecios(null, logistica).empresas().size());
		service.registrarPrecio(producto, goydel, new BigDecimal("20"), logistica);
		assertEquals(0, new BigDecimal("18.5").compareTo(service.listarPrecios(tdem, logistica).precios().getFirst().precio()));
		assertEquals(0, new BigDecimal("20").compareTo(service.listarPrecios(goydel, logistica).precios().getFirst().precio()));
	}

	@Test
	void plazoDeAtencionLoConfiguraLogisticaYSeVeEnElCatalogo() {
		UsuarioPrincipal pyc = principal("pyc@logistica.test", "PYC");
		UsuarioPrincipal logistica = principal("logistica@logistica.test", "LOGISTICA");
		UsuarioPrincipal solicitante = principal("solicitante@logistica.test", "SOLICITANTE");
		UUID producto = producto("AC01003");

		assertThrows(ApiException.class, () -> service.configurarPlazo(producto, 7, pyc));
		assertThrows(ApiException.class, () -> service.configurarPlazo(producto, 3651, logistica));
		assertThrows(ApiException.class, () -> service.configurarPlazo(producto, -1, logistica));

		assertEquals(7, service.configurarPlazo(producto, 7, logistica));
		assertEquals(7, plazoEnCatalogo(producto, solicitante));
		assertEquals(7, plazoEnCatalogo(producto, pyc), "PYC lee el catalogo para registrar precios");

		assertNull(service.configurarPlazo(producto, null, logistica));
		assertNull(plazoEnCatalogo(producto, solicitante));
		assertEquals(2, auditorias(producto, "PLAZO_ATENCION"));
	}

	// ------------------------------------------------------------------

	private Integer plazoEnCatalogo(UUID productId, UsuarioPrincipal principal) {
		return requerimientos.obtenerDatosFormulario(principal).variantes().stream()
				.filter(v -> v.productId().equals(productId)).findFirst()
				.orElseThrow().plazoAtencionDias();
	}

	private int filas(UUID empresa, UUID producto, boolean activo) {
		return jdbc.queryForObject("""
				select count(*) from precio_venta_producto where company_id = ? and product_id = ? and active = ?""",
				Integer.class, empresa, producto, activo);
	}

	private int auditorias(UUID producto, String accion) {
		return jdbc.queryForObject("""
				select count(*) from log_cambios where entidad = 'PRODUCTO' and entidad_id = ? and accion = ?""",
				Integer.class, producto, accion);
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
