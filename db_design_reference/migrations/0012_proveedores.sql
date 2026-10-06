-- 0012_proveedores.sql
-- PROVEEDOR, PROVEEDOR_EMPRESA, PROVEEDOR_PRODUCTO, CONVENIO_PROVEEDOR(_ITEM),
-- ESCALA_PRECIO_PROVEEDOR (grupo PROVEEDORES). La evaluacion/reevaluacion de
-- proveedores (P-LOG-03, 40% de avance) queda fuera de esta fase.

create table if not exists public.proveedor (
  supplier_id uuid primary key default gen_random_uuid(),
  ruc         varchar(11) not null unique,
  legal_name  varchar(220) not null,
  contact_name varchar(150),
  email        varchar(180),
  whatsapp_phone varchar(30),
  supplier_type  varchar(30) not null default 'NO_CRITICO' check (supplier_type in ('CRITICO','NO_CRITICO')),
  preferred_delivery_mode varchar(30) check (preferred_delivery_mode in ('AGENCIA','ALMACEN','RECOJO')),
  active boolean not null default true
);

comment on table public.proveedor is 'Maestro de proveedores y contactos.';

create table if not exists public.proveedor_empresa (
  supplier_id uuid not null references public.proveedor(supplier_id),
  company_id  uuid not null references public.empresa(company_id),
  active      boolean not null default true,
  is_recurrent boolean not null default false,
  prioritize_agreement boolean not null default false,
  agreement_status varchar(30) not null default 'SIN_CONVENIO'
    check (agreement_status in ('SIN_CONVENIO','POR_NEGOCIAR','EN_NEGOCIACION','VIGENTE','VENCIDO')),
  primary key (supplier_id, company_id)
);

comment on table public.proveedor_empresa is 'Habilitacion del proveedor por razon social.';
comment on column public.proveedor_empresa.is_recurrent is
  'Recurrencia detectada NO crea convenio automaticamente (Excel 14_Cambios_V29).';

create table if not exists public.proveedor_producto (
  supplier_product_id uuid primary key default gen_random_uuid(),
  supplier_id uuid not null references public.proveedor(supplier_id),
  product_id  uuid not null references public.producto(product_id),
  variant_id  uuid references public.producto_variante(variant_id),
  supplier_product_code varchar(80),
  reference_lead_days integer,
  active boolean not null default true,
  unique (supplier_id, product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'))
);

comment on table public.proveedor_producto is
  'Productos que el proveedor puede atender; el ultimo costo se deriva de compras validadas, no se edita aqui.';

create table if not exists public.convenio_proveedor (
  agreement_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references public.empresa(company_id),
  supplier_id  uuid not null references public.proveedor(supplier_id),
  agreement_code varchar(50) not null unique,
  start_date   date not null,
  end_date     date not null,
  payment_condition varchar(50) not null,
  delivery_mode      varchar(30) not null,
  max_amount   numeric(14,2),
  currency_code varchar(3) references public.moneda(currency_code),
  approved_by  uuid not null references public.usuario(user_id),
  status       varchar(30) not null default 'VIGENTE' check (status in ('VIGENTE','VENCIDO','SUSPENDIDO')),
  check (end_date >= start_date)
);

comment on table public.convenio_proveedor is
  'Convenio aprobado por Gerencia para usar Orden de Pedido sin RFQ repetitiva.';

create table if not exists public.convenio_proveedor_item (
  agreement_item_id uuid primary key default gen_random_uuid(),
  agreement_id uuid not null references public.convenio_proveedor(agreement_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  uom_id       uuid not null references public.unidad_medida(uom_id),
  currency_code varchar(3) not null references public.moneda(currency_code),
  fixed_unit_cost numeric(14,4) not null,
  lead_days    integer not null,
  min_order_qty numeric(14,3),
  active       boolean not null default true
);

comment on table public.convenio_proveedor_item is 'Precio fijo por producto/variante dentro de un convenio.';

create table if not exists public.escala_precio_proveedor (
  price_scale_id uuid primary key default gen_random_uuid(),
  supplier_id uuid not null references public.proveedor(supplier_id),
  product_id  uuid not null references public.producto(product_id),
  variant_id  uuid references public.producto_variante(variant_id),
  qty_from    numeric(14,3) not null,
  qty_to      numeric(14,3),
  currency_code varchar(3) not null references public.moneda(currency_code),
  unit_cost   numeric(14,4) not null,
  effective_from date not null default current_date,
  effective_to   date
);

comment on table public.escala_precio_proveedor is 'Tramos de costo por volumen (laptops, celulares, etc.).';
