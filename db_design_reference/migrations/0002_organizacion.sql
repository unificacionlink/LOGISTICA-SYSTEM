-- 0002_organizacion.sql
-- EMPRESA, CECO, PROYECTO (grupo ORGANIZACION del diccionario de datos V38 Final).
-- proyecto.responsible_user_id referencia a USUARIO, que aun no existe -> se agrega
-- la FK diferida en 0029_fk_deferred.sql.

create table if not exists public.empresa (
  company_id uuid primary key default gen_random_uuid(),
  code       varchar(20) not null unique,   -- TDEMSRL / METRIN / GOYDELSAC
  legal_name varchar(180) not null,
  ruc        varchar(11) unique,
  active     boolean not null default true
);

comment on table public.empresa is 'Razones sociales del grupo (TDEM/METRIN/GOYDEL) y alcance multiempresa.';
comment on column public.empresa.code is
  'Mismo codigo que usa RRHH-SYSTEM (padron_personal.empresa: TDEMSRL/GOYDELSAC) para que el sync por DNI no requiera tabla de mapeo. METRIN no existe aun en RRHH.';

create table if not exists public.ceco (
  ceco_id    uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.empresa(company_id),
  code       varchar(30) not null,
  name       varchar(180) not null,
  active     boolean not null default true,
  unique (company_id, code)
);

comment on table public.ceco is 'Centro de costo por empresa.';

create table if not exists public.proyecto (
  project_id           uuid primary key default gen_random_uuid(),
  company_id           uuid not null references public.empresa(company_id),
  ceco_id              uuid not null references public.ceco(ceco_id),
  project_code         varchar(50) not null,
  name                 varchar(180) not null,
  client_name          varchar(180),
  responsible_user_id  uuid,  -- FK a usuario, diferida (0029_fk_deferred.sql)
  start_date           date,
  end_date             date,
  status               varchar(30) not null default 'ACTIVO'
                         check (status in ('ACTIVO', 'CERRADO', 'SUSPENDIDO')),
  unique (company_id, project_code)
);

comment on table public.proyecto is 'Proyecto operativo asociado a empresa y CECO.';

-- Seed minimo de empresas (codigos alineados con RRHH-SYSTEM.padron_personal.empresa).
insert into public.empresa (code, legal_name, active) values
  ('TDEMSRL', 'TECNOLOGIA DESARROLLO Y MEDICION S.R.L.', true),
  ('METRIN', 'METRIN (razon social pendiente de confirmar)', true),
  ('GOYDELSAC', 'GOYDEL S.A.C. (razon social pendiente de confirmar)', true)
on conflict (code) do nothing;
