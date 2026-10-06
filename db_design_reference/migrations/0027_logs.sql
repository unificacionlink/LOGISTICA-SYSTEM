-- 0027_logs.sql
-- LOG_EVENTOS, LOG_ERRORES, LOG_CAMBIOS (auditoria transversal). Mismo patron
-- que RRHH-SYSTEM: leidos/escritos por src/services/log.service.ts.

create table if not exists public.log_eventos (
  id          uuid primary key default gen_random_uuid(),
  accion      varchar(120) not null,
  entidad     varchar(60),
  entidad_id  uuid,
  resultado   varchar(10) not null check (resultado in ('OK','ERROR')),
  mensaje     text,
  usuario_id  uuid references public.usuario(user_id),
  extra       jsonb,
  created_at  timestamptz not null default now()
);

create index if not exists idx_log_eventos_entidad on public.log_eventos (entidad, entidad_id);

create table if not exists public.log_errores (
  id         uuid primary key default gen_random_uuid(),
  accion     varchar(120) not null,
  error      text not null,
  extra      jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.log_cambios (
  id          uuid primary key default gen_random_uuid(),
  entidad     varchar(60) not null,
  entidad_id  uuid not null,
  accion      varchar(60) not null,
  antes       jsonb,
  despues     jsonb,
  usuario_id  uuid references public.usuario(user_id),
  created_at  timestamptz not null default now()
);

create index if not exists idx_log_cambios_entidad on public.log_cambios (entidad, entidad_id);

alter table public.log_eventos enable row level security;
alter table public.log_errores enable row level security;
alter table public.log_cambios enable row level security;

-- INSERT: cualquier autenticado (el service ya controla el flujo); nadie
-- falsea usuario_id de otro (mismo hallazgo de seguridad que RRHH-SYSTEM).
drop policy if exists log_eventos_insert on public.log_eventos;
create policy log_eventos_insert on public.log_eventos for insert to authenticated
  with check (usuario_id is null or usuario_id = public.current_usuario_id());
drop policy if exists log_eventos_select on public.log_eventos;
create policy log_eventos_select on public.log_eventos for select to authenticated
  using (public.current_es_logistica_o_admin() or usuario_id = public.current_usuario_id());

drop policy if exists log_errores_insert on public.log_errores;
create policy log_errores_insert on public.log_errores for insert to authenticated with check (true);
drop policy if exists log_errores_select on public.log_errores;
create policy log_errores_select on public.log_errores for select to authenticated
  using (public.current_es_logistica_o_admin());

drop policy if exists log_cambios_insert on public.log_cambios;
create policy log_cambios_insert on public.log_cambios for insert to authenticated
  with check (usuario_id is null or usuario_id = public.current_usuario_id());
drop policy if exists log_cambios_select on public.log_cambios;
create policy log_cambios_select on public.log_cambios for select to authenticated
  using (public.current_es_logistica_o_admin());

revoke update, delete on public.log_eventos, public.log_errores, public.log_cambios from authenticated;
