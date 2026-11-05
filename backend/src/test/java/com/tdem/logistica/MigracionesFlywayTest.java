package com.tdem.logistica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ejecuta TODAS las migraciones Flyway contra {@link BdRrhhSimulada} (Postgres
 * 17 real que imita la BD compartida con RRHH-SYSTEM, DEC-L016). Verifica que LOGISTICA quede completa dentro de su esquema y que
 * RRHH no se toque. Corre offline una vez descargados los binarios de Maven.
 */
class MigracionesFlywayTest {

    private static final String ESQUEMA = "logistica";
    private static EmbeddedPostgres pg;
    private static MigrateResult resultado;

    @BeforeAll
    static void migrarSobreBdQueImitaRrhh() throws Exception {
        pg = BdRrhhSimulada.iniciar();
        resultado = flywayComo(BdRrhhSimulada.USUARIO_APP, "postgres", ESQUEMA).migrate();
    }

    @AfterAll
    static void detener() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void aplicaTodasLasMigracionesEnElEsquemaLogistica() throws SQLException {
        assertEquals(20, resultado.migrationsExecuted);
        assertEquals(589L, contar("select count(*) from logistica.producto"));
        assertEquals(725L, contar("select count(*) from logistica.producto_variante"));
        assertEquals(20L, contar("select count(*) from logistica.flyway_schema_history where success"));
    }

    @Test
    void noCreaNadaEnPublicNiTocaLaRlsDeRrhh() throws SQLException {
        assertEquals(1L, contar("select count(*) from pg_tables where schemaname = 'public'"));
        assertEquals(1L, contar("select count(*) from pg_policies where schemaname = 'public'"));
        assertEquals(1L, contar("select count(*) from pg_tables where schemaname = 'public' and rowsecurity"));
        assertEquals(0L, contar("select count(*) from pg_policies where schemaname = 'logistica'"));
        assertEquals(0L, contar("select count(*) from pg_proc p join pg_namespace n on n.oid = p.pronamespace "
                + "where n.nspname = 'logistica' and p.proname like 'current\\_%'"));
    }

    @Test
    void elRolDeLogisticaNoPuedeLeerTablasDeRrhh() throws SQLException {
        try (Connection c = pg.getDatabase(BdRrhhSimulada.USUARIO_APP, "postgres").getConnection();
             Statement st = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class,
                    () -> st.executeQuery("select * from public.padron_personal"));
            // 42501 = insufficient_privilege (el mensaje depende del idioma del servidor).
            assertEquals("42501", e.getSQLState(), e.getMessage());
        }
    }

    @Test
    void losRolesDeLaApiDeSupabaseNoVenElEsquema() throws SQLException {
        assertEquals(0L, contar("select count(*) from (values ('anon'), ('authenticated'), ('public')) r(rol) "
                + "where r.rol <> 'public' and has_schema_privilege(r.rol, 'logistica', 'USAGE')"));
    }

    @Test
    void reEjecutarNoAplicaNada() {
        assertEquals(0, flywayComo(BdRrhhSimulada.USUARIO_APP, "postgres", ESQUEMA).migrate().migrationsExecuted);
    }

    @Test
    void laGuardiaImpideMigrarSobrePublic() throws SQLException {
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute("create database guardia");
        }
        FlywayException e = assertThrows(FlywayException.class,
                () -> flywayComo("postgres", "guardia", "public").migrate());
        assertTrue(e.getMessage().contains("DEC-L016"), e.getMessage());
    }

    private static Flyway flywayComo(String usuario, String bd, String esquema) {
        DataSource ds = pg.getDatabase(usuario, bd);
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .schemas(esquema)
                .defaultSchema(esquema)
                .createSchemas(false)
                .placeholders(Map.of("schema", esquema))
                .load();
    }

    private static long contar(String sql) throws SQLException {
        try (Connection c = pg.getPostgresDatabase().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
