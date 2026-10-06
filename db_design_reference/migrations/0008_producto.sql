-- 0008_producto.sql
-- PRODUCTO, PRODUCTO_VARIANTE, PRODUCTO_IMAGEN (grupo MAESTROS).
-- producto.approved_request_id referencia SOLICITUD_MAESTRO_PRODUCTO, que se
-- crea despues (0009) -> FK diferida en 0029_fk_deferred.sql.

create table if not exists public.producto (
  product_id      uuid primary key default gen_random_uuid(),
  sku_base        varchar(50) not null unique,
  product_type_id uuid not null references public.tipo_producto(product_type_id),
  family_id       uuid not null references public.familia_producto(family_id),
  subfamily_id    uuid not null references public.subfamilia_producto(subfamily_id),
  name            varchar(220) not null,
  base_uom_id     uuid not null references public.unidad_medida(uom_id),
  supply_class    varchar(30) not null default 'ORDINARIO_BAJA'
                    check (supply_class in ('ORDINARIO_ALTA', 'ORDINARIO_BAJA', 'EXTRAORDINARIO')),
  requires_calibration     varchar(10) not null default 'NO' check (requires_calibration in ('SI','NO','NA')),
  is_asset                 boolean not null default false,
  serial_control            boolean not null default false,
  requires_weight_control   boolean not null default false,
  is_customized             boolean not null default false,
  active                    boolean not null default true,

  approved_request_id uuid,  -- FK a solicitud_maestro_producto, diferida
  approved_by          uuid references public.usuario(user_id),
  approved_at          timestamptz
);

comment on table public.producto is
  'Maestro unico de productos (725 SKU consolidados V38 Final). Alta/modificacion '
  'SOLO via SOLICITUD_MAESTRO_PRODUCTO aprobada por Logistica (ver 0009 y 0010_solicitud_maestro_producto.sql).';

create table if not exists public.producto_variante (
  variant_id             uuid primary key default gen_random_uuid(),
  product_id             uuid not null references public.producto(product_id),
  attribute_type         varchar(40) check (attribute_type in ('TALLA','COLOR','MODELO','CLASE','CLASE + TALLA','OTRO')),
  attribute_value        varchar(80),
  attribute_code         varchar(20) not null default '00',  -- '00' si no tiene atributo
  internal_variant_code  varchar(80) not null unique,
  active                 boolean not null default true
);

comment on table public.producto_variante is 'Talla/color/atributo principal sin alterar el SKU base.';

create table if not exists public.producto_imagen (
  product_image_id uuid primary key default gen_random_uuid(),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  document_id  uuid not null references public.documento(document_id),
  is_primary   boolean not null default false,
  alt_text     varchar(220),
  display_order integer not null default 0,
  active       boolean not null default true,
  created_at   timestamptz not null default now(),
  created_by   uuid not null references public.usuario(user_id)
);

comment on table public.producto_imagen is
  'Imagenes de producto/variante. El archivo fisico va a storage; aqui solo la referencia a DOCUMENTO.';

-- Solo una imagen principal activa por producto+variante (RN del Excel 16_Producto_Imagen_V32).
create unique index if not exists uq_producto_imagen_principal
  on public.producto_imagen (product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'))
  where is_primary = true and active = true;
