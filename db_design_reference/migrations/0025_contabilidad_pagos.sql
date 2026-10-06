-- 0025_contabilidad_pagos.sql
-- DISTRIBUCION_COSTO (grupo CONTABILIDAD), GESTION_PAGO (grupo PAGOS -- solo
-- estado; el dominio completo de pagos vive en otro aplicativo, Logistica/Compras
-- solo consulta).

create table if not exists public.distribucion_costo (
  cost_distribution_id uuid primary key default gen_random_uuid(),
  source_type varchar(30) not null check (source_type in ('FACTURA_ITEM','CAJA_CHICA','ORDEN_ITEM')),
  source_id   uuid not null,  -- polimorfico, sin FK directa
  ceco_id    uuid not null references public.ceco(ceco_id),
  project_id uuid references public.proyecto(project_id),
  amount     numeric(14,2) not null,
  percentage numeric(8,4),
  created_at timestamptz not null default now()
);

comment on table public.distribucion_costo is 'Distribucion de factura/caja chica/orden por CECO/proyecto.';

create index if not exists idx_distribucion_costo_source on public.distribucion_costo (source_type, source_id);

create table if not exists public.gestion_pago (
  payment_management_id uuid primary key default gen_random_uuid(),
  invoice_id uuid not null references public.factura_proveedor(invoice_id),
  order_id   uuid references public.orden_abastecimiento(order_id),
  status varchar(30) not null default 'PENDIENTE'
    check (status in ('PENDIENTE','EN_GESTION','PROGRAMADO','PAGADO','OBSERVADO')),
  payment_condition varchar(40) not null check (payment_condition in ('CONTADO','ANTICIPO','CREDITO')),
  scheduled_date date,
  payment_date   date,
  amount numeric(14,2) not null,
  currency_code varchar(3) not null references public.moneda(currency_code),
  payment_proof_document_id uuid references public.documento(document_id),
  updated_at timestamptz not null default now()
);

comment on table public.gestion_pago is
  'Estado del pago; Compras solo CONSULTA, el aplicativo de Pagos mantiene el dominio completo.';

drop trigger if exists trg_gestion_pago_updated_at on public.gestion_pago;
create trigger trg_gestion_pago_updated_at
  before update on public.gestion_pago
  for each row execute function public.set_updated_at();

create index if not exists idx_gestion_pago_invoice on public.gestion_pago (invoice_id);
