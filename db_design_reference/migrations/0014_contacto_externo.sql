-- 0014_contacto_externo.sql
-- CONTACTO_EXTERNO (grupo REQUERIMIENTOS): solicitante/cliente externo que no
-- requiere login al ERP. customer_id referencia CLIENTE_DESTINO (grupo
-- COSTOS_VENTAS), que es Fase 2 -- se deja como columna simple sin FK.

create table if not exists public.contacto_externo (
  external_contact_id uuid primary key default gen_random_uuid(),
  company_id  uuid references public.empresa(company_id),
  customer_id uuid,  -- FK a CLIENTE_DESTINO cuando exista (Fase 2)
  name        varchar(180) not null,
  email       varchar(180),
  whatsapp_phone varchar(30),
  tracking_channel varchar(20) not null default 'EMAIL' check (tracking_channel in ('WHATSAPP','EMAIL')),
  active      boolean not null default true
);

comment on table public.contacto_externo is 'Contacto de solicitante/cliente externo que no requiere login al ERP.';
comment on column public.contacto_externo.customer_id is
  'Referencia a CLIENTE_DESTINO (Fase 2, fuera de alcance). Sin FK hasta que esa tabla exista.';
