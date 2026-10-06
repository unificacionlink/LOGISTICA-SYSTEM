-- rrhh_db_esquema_logistica.sql  (DEC-L016)
--
-- Prepara la BD de Supabase de RRHH-SYSTEM para alojar el ERP Logistico en su
-- PROPIO esquema, con un rol dedicado que no puede leer ni modificar las
-- tablas de RRHH. Se ejecuta UNA sola vez, a mano, en el SQL Editor del
-- proyecto de RRHH (como postgres). Flyway (backend) crea todo lo demas
-- dentro de "logistica" al primer arranque.
--
-- ANTES DE EJECUTAR: reemplazar CAMBIAR_ESTA_PASSWORD por una contrasena
-- fuerte (y guardarla en application-local.yml / Render como DB_PASSWORD).
--
-- Conexion del backend (pooler Supavisor, modo sesion, puerto 5432):
--   DB_URL      = jdbc:postgresql://aws-1-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require  (proyecto RRHH: aws-1, no aws-0)
--   DB_USERNAME = logistica_app.<ref_del_proyecto_rrhh>
--   DB_SCHEMA   = logistica
--
-- Tambien sirve tal cual para el dia en que LOGISTICA se separe a su propio
-- proyecto (ver "Como separar LOGISTICA a su propia BD" en 03_GUIA_OPERATIVA.md).

create role logistica_app with login password 'CAMBIAR_ESTA_PASSWORD';

-- postgres necesita ser miembro para poder asignarle el esquema y para
-- inspeccionar las tablas de LOGISTICA desde el SQL Editor.
grant logistica_app to postgres;

create schema logistica authorization logistica_app;

-- Nadie mas usa el esquema: ni PUBLIC ni los roles de la API de Supabase
-- (anon/authenticated). Ademas, "logistica" NO se agrega a los "Exposed
-- schemas" de la Data API (Settings -> API), asi que PostgREST no lo publica.
revoke all on schema logistica from public;
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    execute 'revoke all on schema logistica from anon, authenticated';
  end if;
end $$;

alter role logistica_app set search_path = logistica;
