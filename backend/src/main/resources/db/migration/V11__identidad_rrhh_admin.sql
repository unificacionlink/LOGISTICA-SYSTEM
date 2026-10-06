-- V11 — Lote 8a: identidad sincronizada con RRHH-SYSTEM y administracion (DEC-L024).
--
-- Los usuarios que llegan por sync (o por alta manual) no tienen contrasena:
-- ADMIN genera una temporal y el usuario debe cambiarla en su primer ingreso
-- (no hay correo en v1.0, DEC-L023).

alter table ${schema}.usuario
  add column if not exists must_change_password boolean not null default false;

comment on column ${schema}.usuario.must_change_password is
  'true tras una contrasena temporal generada por ADMIN: el frontend obliga a cambiarla antes de operar.';
