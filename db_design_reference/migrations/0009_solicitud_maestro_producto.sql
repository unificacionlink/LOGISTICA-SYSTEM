-- 0009_solicitud_maestro_producto.sql
-- SOLICITUD_MAESTRO_PRODUCTO (grupo MAESTROS). Unica puerta de entrada para
-- crear/modificar/inactivar PRODUCTO (RN: "no crear producto directamente").

create table if not exists public.solicitud_maestro_producto (
  product_master_request_id uuid primary key default gen_random_uuid(),
  request_code varchar(50) not null unique,
  request_type varchar(30) not null
    check (request_type in ('ALTA_PRODUCTO','NUEVA_VARIANTE','MODIFICACION','INACTIVACION')),
  company_id   uuid not null references public.empresa(company_id),
  requested_by uuid not null references public.usuario(user_id),
  requested_at timestamptz not null default now(),

  proposed_product_type_id uuid not null references public.tipo_producto(product_type_id),
  proposed_family_id       uuid not null references public.familia_producto(family_id),
  proposed_subfamily_id    uuid not null references public.subfamilia_producto(subfamily_id),
  proposed_name            varchar(220) not null,
  proposed_uom_id           uuid not null references public.unidad_medida(uom_id),
  proposed_attribute_type  varchar(40) check (proposed_attribute_type in ('TALLA','COLOR','MODELO','OTRO')),
  proposed_attribute_value varchar(80),

  technical_description text,
  justification         text not null,
  document_id            uuid references public.documento(document_id),

  duplicate_check_status varchar(30) not null default 'SIN_COINCIDENCIA'
    check (duplicate_check_status in ('SIN_COINCIDENCIA','POSIBLE_DUPLICADO','DUPLICADO')),
  status varchar(40) not null default 'BORRADOR'
    check (status in (
      'BORRADOR','PENDIENTE_APROBACION_LOGISTICA','OBSERVADO','RECHAZADO','APROBADO','DUPLICADO'
    )),
  reviewed_by      uuid references public.usuario(user_id),
  reviewed_at      timestamptz,
  review_comment   text,
  generated_product_id uuid references public.producto(product_id)
);

comment on table public.solicitud_maestro_producto is
  'Solicitud de alta/modificacion/inactivacion de producto; solo Logistica aprueba y activa el maestro.';

create index if not exists idx_solicitud_maestro_producto_status
  on public.solicitud_maestro_producto (status);
