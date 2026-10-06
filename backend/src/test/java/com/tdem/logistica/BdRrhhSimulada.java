package com.tdem.logistica;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Postgres 17 real (embebido, sin Docker) que imita la BD de Supabase
 * compartida con RRHH-SYSTEM (DEC-L016): roles anon/authenticated, una tabla
 * de RRHH en public con RLS + policy, y el esquema "logistica" preparado con
 * db_design_reference/setup/rrhh_db_esquema_logistica.sql tal cual.
 */
final class BdRrhhSimulada {

    static final String USUARIO_APP = "logistica_app";
    static final String PASSWORD_APP = "test";

    private BdRrhhSimulada() {
    }

    static EmbeddedPostgres iniciar() throws IOException, SQLException {
        EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement st = c.createStatement()) {
            st.execute("create role anon nologin; create role authenticated nologin;");
            st.execute("""
                    create table public.padron_personal (id int primary key, dni text);
                    insert into public.padron_personal values (1, '12345678');
                    grant select on public.padron_personal to authenticated;
                    alter table public.padron_personal enable row level security;
                    create policy padron_select on public.padron_personal for select to authenticated using (true);
                    """);
            st.execute(scriptSetup());
        }
        return pg;
    }

    static String jdbcUrl(EmbeddedPostgres pg) {
        return pg.getJdbcUrl("postgres", "postgres");
    }

    private static String scriptSetup() throws IOException {
        // Los tests corren con cwd = backend/; el script vive en la raiz del repo.
        Path script = Path.of("..", "db_design_reference", "setup", "rrhh_db_esquema_logistica.sql");
        return Files.readString(script, StandardCharsets.UTF_8)
                .replace("CAMBIAR_ESTA_PASSWORD", PASSWORD_APP);
    }
}
