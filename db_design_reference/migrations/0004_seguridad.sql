-- 0004_seguridad.sql
-- ROL, PERMISO, ROL_PERMISO, USUARIO, USUARIO_ROL, USUARIO_CECO (grupo SEGURIDAD).
--
-- USUARIO es identidad/autorizacion del ERP (login + rol + CECO visible),
-- NO un mantenedor de personal. El diccionario original de Jonathan no preveia
-- la integracion con RRHH-SYSTEM, asi que se agregan 2 columnas ADITIVAS
-- (fuente, source_synced_at) fuera de su diseño base -- ver docs/04_DECISIONES.md
-- DEC-L002. El resto de columnas (employee_external_id, corporate_email,
-- display_name, identity_provider_id, active) son las que ya especifico Jonathan.

create table if not exists public.rol (
  role_id uuid primary key default gen_random_uuid(),
  code    varchar(40) not null unique,  -- SOLICITANTE / LOGISTICA / APROBADOR / ADMIN
  name    varchar(100) not null,
  active  boolean not null default true
);

comment on table public.rol is 'Roles funcionales del ERP Logistico.';

create table if not exists public.permiso (
  permission_id uuid primary key default gen_random_uuid(),
  code        varchar(80) not null unique,  -- ej. COSTO_COMPRA_VER
  module      varchar(60) not null,
  description varchar(220) not null
);

comment on table public.permiso is 'Permisos atomicos; no depender del nombre del rol para autorizar.';

create table if not exists public.rol_permiso (
  role_id       uuid not null references public.rol(role_id),
  permission_id uuid not null references public.permiso(permission_id),
  primary key (role_id, permission_id)
);

create table if not exists public.usuario (
  user_id               uuid primary key default gen_random_uuid(),
  company_id            uuid references public.empresa(company_id),  -- null = corporativo/multiempresa
  employee_external_id  varchar(50),   -- DNI/ID proveniente de RRHH-SYSTEM cuando fuente=RRHH_SYNC
  corporate_email       varchar(180) not null unique,
  display_name          varchar(180) not null,
  identity_provider_id  varchar(180), -- id en Supabase Auth
  active                boolean not null default true,

  -- Extension DEC-L002 (fuera del diccionario original de Jonathan):
  fuente             varchar(20) not null default 'MANUAL'
                        check (fuente in ('RRHH_SYNC', 'MANUAL')),
  source_synced_at   timestamptz,  -- ultima vez que personal_sync.service actualizo esta fila

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.usuario is
  'Identidad/autorizacion del ERP (login, rol, CECO visible). NO es mantenedor de personal: '
  'para TDEM/GOYDEL se llena por sync de solo lectura desde RRHH-SYSTEM (fuente=RRHH_SYNC); '
  'para METRIN/GOYDEL mientras RRHH no los cubra, por alta manual acotada (fuente=MANUAL).';
comment on column public.usuario.employee_external_id is
  'DNI (u otro id) proveniente de RRHH-SYSTEM. Unique parcial definido mas abajo.';

create unique index if not exists uq_usuario_employee_external_id
  on public.usuario (employee_external_id) where employee_external_id is not null;

create unique index if not exists uq_usuario_identity_provider_id
  on public.usuario (identity_provider_id) where identity_provider_id is not null;

drop trigger if exists trg_usuario_updated_at on public.usuario;
create trigger trg_usuario_updated_at
  before update on public.usuario
  for each row execute function public.set_updated_at();

create table if not exists public.usuario_rol (
  user_id    uuid not null references public.usuario(user_id),
  role_id    uuid not null references public.rol(role_id),
  company_id uuid references public.empresa(company_id),  -- null = rol aplica a todas las empresas del usuario
  primary key (user_id, role_id, company_id)
);

comment on table public.usuario_rol is 'Relacion usuario-rol por empresa (un usuario puede tener distinto rol por empresa).';

create table if not exists public.usuario_ceco (
  user_id uuid not null references public.usuario(user_id),
  ceco_id uuid not null references public.ceco(ceco_id),
  primary key (user_id, ceco_id)
);

comment on table public.usuario_ceco is 'CECO/proyectos visibles para el usuario (ambito de un SOLICITANTE/APROBADOR).';

-- Seed de roles (ver docs_base/Matriz_Funcionalidades..., hoja 07_Perfiles_V38:
-- SOLICITANTE/LOGISTICA/ADMIN; APROBADOR se agrega porque REGLA_APROBACION
-- exige rangos monetarios que el prototipo no separaba explicitamente de LOGISTICA).
insert into public.rol (code, name) values
  ('SOLICITANTE', 'Solicitante'),
  ('LOGISTICA', 'Logistica'),
  ('APROBADOR', 'Aprobador / Gerencia'),
  ('ADMIN', 'Administrador')
on conflict (code) do nothing;
