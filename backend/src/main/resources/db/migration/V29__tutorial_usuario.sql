-- V29 — Recorrido guiado para usuarios nuevos (DEC-L055, v0.41.0).
--
-- Que recorridos ya vio (o salto) cada usuario, para ofrecerlo solo en su
-- primer ingreso y no repetirlo en otro equipo. Se puede volver a ver desde
-- Ayuda sin tocar esta tabla.

create table if not exists ${schema}.usuario_tutorial (
  user_id       uuid not null references ${schema}.usuario(user_id) on delete cascade,
  tutorial_code varchar(40) not null,
  status        varchar(12) not null check (status in ('COMPLETADO', 'OMITIDO')),
  updated_at    timestamptz not null default now(),
  primary key (user_id, tutorial_code)
);

comment on table ${schema}.usuario_tutorial is
  'Recorridos guiados vistos (COMPLETADO) o saltados (OMITIDO) por usuario (DEC-L055).';
