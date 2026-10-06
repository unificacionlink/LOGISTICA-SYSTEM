-- 0001_init_common.sql
-- Extensiones y utilidades compartidas por todas las tablas del ERP Logistico.

create extension if not exists pgcrypto;

-- Trigger generico: mantiene updated_at al dia en cualquier tabla que lo tenga.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

comment on function public.set_updated_at() is
  'Trigger BEFORE UPDATE generico: setea updated_at = now(). Igual patron que RRHH-SYSTEM.';
