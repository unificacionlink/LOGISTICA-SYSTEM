-- V3__usuario_password_hash.sql
-- Supabase Auth guardaba las credenciales en auth.users (tabla que no
-- migra). Spring Security necesita su propia columna de credenciales en
-- ${schema}.usuario para poder autenticar con BCrypt + JWT propio.

alter table ${schema}.usuario
  add column if not exists password_hash varchar(100);

comment on column ${schema}.usuario.password_hash is
  'Hash BCrypt de la contrasena, gestionado por Spring Security. Null para '
  'usuarios que todavia no se migraron desde Supabase Auth.';
