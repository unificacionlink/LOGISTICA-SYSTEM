-- 0019_cotizaciones_consulta_rfq.sql
-- CONSULTA_PROVEEDOR(_ITEM), RFQ(_ITEM/_ITEM_ORIGEN), COMUNICACION_PROVEEDOR
-- (grupo COTIZACIONES). RN-005: consulta de disponibilidad != cotizacion formal
-- (tablas separadas). RN-006: una RFQ puede tener varios items del mismo
-- proveedor, cada uno conserva su origen REQ/CECO via RFQ_ITEM_ORIGEN.

create table if not exists public.consulta_proveedor (
  supplier_inquiry_id uuid primary key default gen_random_uuid(),
  company_id  uuid not null references public.empresa(company_id),
  supplier_id uuid not null references public.proveedor(supplier_id),
  channel     varchar(20) not null check (channel in ('WHATSAPP','EMAIL','TELEFONO')),
  status      varchar(30) not null default 'PENDIENTE' check (status in ('PENDIENTE','RESPONDIDA','CERRADA')),
  sent_at     timestamptz,
  responded_at timestamptz,
  created_by  uuid not null references public.usuario(user_id)
);

comment on table public.consulta_proveedor is
  'Consulta previa de disponibilidad/capacidad (RN-005: distinta de una cotizacion formal).';

create table if not exists public.consulta_proveedor_item (
  supplier_inquiry_item_id uuid primary key default gen_random_uuid(),
  supplier_inquiry_id uuid not null references public.consulta_proveedor(supplier_inquiry_id),
  product_id uuid not null references public.producto(product_id),
  variant_id uuid references public.producto_variante(variant_id),
  qty_requested numeric(14,3) not null,
  availability_status varchar(30) not null default 'PENDIENTE'
    check (availability_status in ('DISPONIBLE','PARCIAL','NO_DISPONIBLE','ALTERNATIVA','PENDIENTE')),
  qty_available numeric(14,3),
  alternative_description text,
  observation text
);

comment on table public.consulta_proveedor_item is 'Disponibilidad respondida por producto consultado.';

create table if not exists public.rfq (
  rfq_id   uuid primary key default gen_random_uuid(),
  rfq_code varchar(50) not null unique,
  company_id  uuid not null references public.empresa(company_id),
  supplier_id uuid not null references public.proveedor(supplier_id),
  issue_date  date not null default current_date,
  payment_condition varchar(40) check (payment_condition in ('CONTADO','ANTICIPO','CREDITO_30')),
  delivery_mode      varchar(30) check (delivery_mode in ('AGENCIA','ALMACEN_PROVEEDOR','ALMACEN_CLIENTE')),
  delivery_location  varchar(220),
  requested_lead_days integer,
  penalty_rule_id     uuid references public.regla_penalidad(penalty_rule_id),
  currency_requested  varchar(3) references public.moneda(currency_code),
  status varchar(30) not null default 'BORRADOR' check (status in ('BORRADOR','ENVIADA','RESPONDIDA','CERRADA')),
  created_by uuid not null references public.usuario(user_id),
  created_at timestamptz not null default now()
);

comment on table public.rfq is 'Cabecera formal de Solicitud de Cotizacion por proveedor.';

create table if not exists public.rfq_item (
  rfq_item_id uuid primary key default gen_random_uuid(),
  rfq_id     uuid not null references public.rfq(rfq_id),
  product_id uuid not null references public.producto(product_id),
  variant_id uuid references public.producto_variante(variant_id),
  uom_id     uuid not null references public.unidad_medida(uom_id),
  qty        numeric(14,3) not null,  -- cantidad consolidada
  technical_specification text
);

comment on table public.rfq_item is 'Items enviados en una RFQ (cantidad ya consolidada).';

create table if not exists public.rfq_item_origen (
  rfq_item_origin_id uuid primary key default gen_random_uuid(),
  rfq_item_id     uuid not null references public.rfq_item(rfq_item_id),
  request_item_id uuid not null references public.requerimiento_item(request_item_id),
  ceco_id    uuid not null references public.ceco(ceco_id),
  project_id uuid references public.proyecto(project_id),
  qty_origin numeric(14,3) not null check (qty_origin > 0)
);

comment on table public.rfq_item_origen is
  'RN-006: conserva REQ/CECO/proyecto detras de cada cantidad consolidada de un item de RFQ.';

create table if not exists public.comunicacion_proveedor (
  communication_id uuid primary key default gen_random_uuid(),
  supplier_id uuid not null references public.proveedor(supplier_id),
  entity_type varchar(40) not null check (entity_type in ('CONSULTA','RFQ','ORDEN','INCIDENCIA','PAGO')),
  entity_id   uuid not null,  -- polimorfico, sin FK directa
  channel   varchar(20) not null check (channel in ('WHATSAPP','EMAIL','TELEFONO')),
  direction varchar(10) not null check (direction in ('SALIENTE','ENTRANTE')),
  recipient varchar(180),
  status    varchar(30) not null default 'PREPARADO' check (status in ('PREPARADO','ENVIADO','RESPONDIDO')),
  prepared_at timestamptz,
  sent_at     timestamptz,
  responded_at timestamptz,
  document_id uuid references public.documento(document_id),
  created_by  uuid not null references public.usuario(user_id)
);

comment on table public.comunicacion_proveedor is
  'Trazabilidad de WhatsApp/Correo/Llamada en consultas, RFQ, ordenes, incidencias y pagos.';

create index if not exists idx_comunicacion_proveedor_entity on public.comunicacion_proveedor (entity_type, entity_id);
