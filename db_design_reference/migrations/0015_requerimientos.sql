-- 0015_requerimientos.sql
-- REQUERIMIENTO, REQUERIMIENTO_ITEM (grupo REQUERIMIENTOS). Incluye los
-- ajustes V37 del diccionario (tracking, punto de entrega, snapshots).

create table if not exists public.requerimiento (
  request_id   uuid primary key default gen_random_uuid(),
  request_code varchar(50) not null,
  company_id   uuid not null references public.empresa(company_id),
  ceco_id      uuid not null references public.ceco(ceco_id),
  project_id   uuid references public.proyecto(project_id),
  requester_user_id     uuid references public.usuario(user_id),
  requester_type         varchar(20) not null check (requester_type in ('INTERNO','EXTERNO')),
  external_contact_id    uuid references public.contacto_externo(external_contact_id),
  priority     varchar(20) not null default 'NORMAL' check (priority in ('NORMAL','URGENTE','CRITICA')),
  purpose      varchar(30) not null check (purpose in ('CONSUMO','VENTA','PRESTAMO','IMPLEMENTACION')),
  justification text not null,
  status       varchar(40) not null default 'BORRADOR'
    check (status in (
      'BORRADOR','PENDIENTE_APROBACION','VERIFICACION_STOCK','EN_ATENCION',
      'ATENCION_PARCIAL','ATENCION_COMPLETA','RECHAZADO'
    )),
  sla_due_at     timestamptz,
  required_date  date,  -- fecha requerida por el solicitante; permite detectar atencion "mismo dia"

  -- V37 (Excel 20_Tracking_REQ_NI_V37): fecha programada separada de la requerida,
  -- y snapshot de direccion/modalidad/valor al aprobar (preserva historico).
  scheduled_delivery_date date,
  delivery_point_id        uuid references public.punto_entrega(delivery_point_id),
  delivery_address_snapshot text,
  requested_delivery_mode  varchar(40)
    check (requested_delivery_mode in ('TERRESTRE_NORMAL','AGENCIA','RECOJO','PROPIO','COURIER','OTRO')),
  currency_code            varchar(3) references public.moneda(currency_code),
  total_value_net_snapshot numeric(14,2),

  created_at timestamptz not null default now(),

  unique (company_id, request_code),
  check (
    (requester_type = 'INTERNO' and requester_user_id is not null and external_contact_id is null)
    or
    (requester_type = 'EXTERNO' and external_contact_id is not null and requester_user_id is null)
  )
);

comment on table public.requerimiento is 'Canal unico de solicitudes (RN: no se define aqui OC ni Caja Chica).';

create table if not exists public.requerimiento_item (
  request_item_id uuid primary key default gen_random_uuid(),
  request_id   uuid not null references public.requerimiento(request_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  uom_id       uuid not null references public.unidad_medida(uom_id),
  qty_requested numeric(14,3) not null check (qty_requested > 0),
  technical_requirement text,
  observation   text,
  unit_value_snapshot numeric(14,4)  -- V37: valor mostrado al emitir/aprobar (documental, no se recalcula)
);

comment on table public.requerimiento_item is 'Items solicitados dentro de un requerimiento.';

create index if not exists idx_requerimiento_company_status on public.requerimiento (company_id, status);
create index if not exists idx_requerimiento_requester on public.requerimiento (requester_user_id);
create index if not exists idx_requerimiento_item_request on public.requerimiento_item (request_id);
