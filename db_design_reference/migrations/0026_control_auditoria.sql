-- 0026_control_auditoria.sql
-- VERIFICACION_ADQUISICION(_ITEM), AUDITORIA (grupo CONTROL).

create table if not exists public.verificacion_adquisicion (
  verification_id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orden_abastecimiento(order_id),
  payment_status_ok boolean not null default false,
  warehouse_closed  boolean not null default false,
  invoice_linked    boolean not null default false,
  amounts_match     boolean not null default false,
  currency_match    boolean not null default false,
  result varchar(30) not null default 'OBSERVADO' check (result in ('CONFORME','OBSERVADO')),
  conclusion text,
  verified_by uuid not null references public.usuario(user_id),
  verified_at timestamptz not null default now()
);

comment on table public.verificacion_adquisicion is 'Cierre documental y operativo de la orden.';

create table if not exists public.verificacion_adquisicion_item (
  verification_item_id uuid primary key default gen_random_uuid(),
  verification_id uuid not null references public.verificacion_adquisicion(verification_id),
  order_item_id   uuid not null references public.orden_item(order_item_id),
  qty_ordered  numeric(14,3) not null,
  qty_received numeric(14,3) not null,
  qty_invoiced numeric(14,3) not null,
  functionality_ok varchar(10) not null default 'NA' check (functionality_ok in ('Y','N','NA')),
  standards_ok      varchar(10) not null default 'NA' check (standards_ok in ('Y','N','NA')),
  specification_ok  varchar(10) not null default 'NA' check (specification_ok in ('Y','N','NA')),
  observation text
);

comment on table public.verificacion_adquisicion_item is 'Verificacion producto a producto (pedido vs recibido vs facturado).';

create table if not exists public.auditoria (
  audit_id uuid primary key default gen_random_uuid(),
  company_id  uuid references public.empresa(company_id),
  entity_type varchar(40) not null,
  entity_id   uuid not null,  -- polimorfico, sin FK directa
  action      varchar(80) not null,
  from_state  varchar(40),
  to_state    varchar(40),
  user_id     uuid not null references public.usuario(user_id),
  comment     text,
  created_at  timestamptz not null default now()
);

comment on table public.auditoria is 'Historial inmutable de acciones y cambios de estado.';

create index if not exists idx_auditoria_entity on public.auditoria (entity_type, entity_id);
revoke update, delete on public.auditoria from authenticated;
