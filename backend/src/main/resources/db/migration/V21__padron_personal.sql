-- ============================================================
-- V21 — Padron de personal (DEC-L041)
-- Personal de cada empresa, separado de las cuentas de acceso (usuario).
-- TDEM llega del sync con RR.HH. (todo el personal activo, tenga o no
-- correo); GOYDEL y METRIN, por la carga Excel. La cuenta se crea aparte
-- ("Dar acceso"): personal.user_id apunta a ella.
-- Las listas no se mezclan entre empresas: la clave es empresa + DNI.
-- ============================================================

create table if not exists ${schema}.personal (
  personal_id      uuid primary key default gen_random_uuid(),
  company_id       uuid not null references ${schema}.empresa(company_id),
  dni              varchar(20)  not null,
  full_name        varchar(180) not null,
  email            varchar(180),
  position         varchar(150),
  ceco_id          uuid references ${schema}.ceco(ceco_id),
  fuente           varchar(20) not null check (fuente in ('RRHH_SYNC', 'MANUAL')),
  active           boolean not null default true,
  user_id          uuid references ${schema}.usuario(user_id),
  source_synced_at timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  constraint uq_personal_empresa_dni unique (company_id, dni)
);

create index if not exists ix_personal_user on ${schema}.personal (user_id) where user_id is not null;

comment on table ${schema}.personal is
  'Padron de personal por empresa (DEC-L041). No da acceso: la cuenta es usuario (user_id). '
  'TDEM se sincroniza desde RRHH-SYSTEM; GOYDEL/METRIN se cargan por Excel.';
comment on column ${schema}.personal.email is
  'Correo que trae RR.HH. o el Excel (puede faltar). El correo de ingreso es el de usuario.';

drop trigger if exists trg_personal_updated_at on ${schema}.personal;
create trigger trg_personal_updated_at
  before update on ${schema}.personal
  for each row execute function ${schema}.set_updated_at();

-- Las cuentas que ya existen con DNI y empresa pasan al padron, vinculadas.
-- CECO de planilla: el primero asignado de su propia empresa.
insert into ${schema}.personal (company_id, dni, full_name, email, position, ceco_id, fuente, active, user_id,
                                source_synced_at)
select u.company_id, u.employee_external_id, u.display_name, u.corporate_email, u.cargo,
       (select uc.ceco_id
          from ${schema}.usuario_ceco uc
          join ${schema}.ceco c on c.ceco_id = uc.ceco_id
         where uc.user_id = u.user_id and c.company_id = u.company_id
         order by c.code
         limit 1),
       u.fuente, u.active, u.user_id, u.source_synced_at
  from ${schema}.usuario u
 where u.employee_external_id is not null
   and u.company_id is not null
on conflict (company_id, dni) do nothing;
