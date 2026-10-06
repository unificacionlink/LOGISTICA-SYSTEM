-- 0013_puntos_entrega.sql
-- PUNTO_ENTREGA (grupo LOGISTICA, subset: solo el maestro de puntos que usa
-- REQUERIMIENTO.delivery_point_id; el modulo de Envios/Tracking fisico completo
-- queda en Fase 2).

create table if not exists public.punto_entrega (
  delivery_point_id uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.empresa(company_id),
  ceco_id    uuid references public.ceco(ceco_id),
  point_type varchar(30) not null check (point_type in ('CECO','ALMACEN','AGENCIA','OFICINA','DOMICILIO','OTRO')),
  name       varchar(180) not null,
  address    text not null,
  district   varchar(120),
  province   varchar(120),
  department varchar(120),
  reference  text,
  active     boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid not null references public.usuario(user_id)
);

comment on table public.punto_entrega is
  'Maestro de puntos/direcciones de entrega: CECO, almacen, agencia, oficina, domicilio u otro.';
