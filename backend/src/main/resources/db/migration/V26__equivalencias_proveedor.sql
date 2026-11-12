-- V26: como llama cada proveedor a cada producto (DEC-L053). Se aprende cuando
-- Logistica confirma en "Revisar cotizacion" que una linea del documento del
-- proveedor es uno de nuestros productos; la proxima lectura de ese proveedor
-- la reconoce sola. Solo agrega una tabla y dos columnas opcionales.

create table ${schema}.proveedor_equivalencia (
  equivalence_id       uuid primary key default gen_random_uuid(),
  supplier_id          uuid not null references ${schema}.proveedor(supplier_id),
  product_id           uuid not null references ${schema}.producto(product_id),
  variant_id           uuid references ${schema}.producto_variante(variant_id),
  supplier_code        varchar(60),
  supplier_description varchar(250) not null,
  description_key      varchar(250) not null,
  times_used           integer not null default 1,
  last_used_at         timestamptz not null default now(),
  created_by           uuid references ${schema}.usuario(user_id),
  created_at           timestamptz not null default now()
);

create unique index ux_proveedor_equivalencia_desc on ${schema}.proveedor_equivalencia (supplier_id, description_key);
create index idx_proveedor_equivalencia_producto on ${schema}.proveedor_equivalencia (product_id);

comment on table ${schema}.proveedor_equivalencia is
  'Nombre/codigo del proveedor para un producto nuestro (DEC-L053). description_key = '
  'descripcion normalizada (mayusculas, sin tildes, sin montos ni numeros sueltos). '
  'La ultima confirmacion de una misma descripcion gana.';

-- Lo que el proveedor escribio en su cotizacion: para la OC ("Ref. proveedor") y auditar.
alter table ${schema}.cotizacion_item
  add column supplier_code varchar(60),
  add column supplier_description varchar(250);
