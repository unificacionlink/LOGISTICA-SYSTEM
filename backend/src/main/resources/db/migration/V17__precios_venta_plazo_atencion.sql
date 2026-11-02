-- V17__precios_venta_plazo_atencion.sql
-- Lote 13 (DEC-L035, V38.7): precio de venta por empresa y plazo de atencion por SKU.

-- Precio de venta interno por empresa + SKU (salePrices de la V38.7). Registrar
-- un precio nuevo desactiva el vigente: las filas inactivas son el historial.
create table ${schema}.precio_venta_producto (
  sale_price_id  uuid primary key default gen_random_uuid(),
  company_id     uuid not null references ${schema}.empresa(company_id),
  product_id     uuid not null references ${schema}.producto(product_id),
  sale_price     numeric(14,4) not null check (sale_price > 0),
  currency_code  varchar(3) not null default 'PEN' references ${schema}.moneda(currency_code)
                 check (currency_code = 'PEN'),
  active         boolean not null default true,
  created_by     uuid not null references ${schema}.usuario(user_id),
  created_at     timestamptz not null default now()
);

comment on table ${schema}.precio_venta_producto is
  'Precio de venta interno por empresa y SKU (PEN). Una fila activa por empresa+SKU; las inactivas son historial.';

create unique index ux_precio_venta_vigente
  on ${schema}.precio_venta_producto (company_id, product_id) where active;

-- Plazo de atencion en dias calendario (attentionDaysCalendar). Null = sin configurar.
alter table ${schema}.producto
  add column attention_days            integer check (attention_days between 0 and 3650),
  add column attention_days_updated_by uuid references ${schema}.usuario(user_id),
  add column attention_days_updated_at timestamptz;

comment on column ${schema}.producto.attention_days is
  'Plazo de atencion del SKU en dias calendario (0-3650). Lo configura Logistica; null = sin configurar.';
