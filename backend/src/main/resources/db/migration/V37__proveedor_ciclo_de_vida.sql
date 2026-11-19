-- Lote 19a (DEC-L069): ficha del proveedor y ciclo de incorporacion
-- Borrador -> Pendiente de revision -> Habilitado / Observado / Rechazado.
-- Compras sigue leyendo solo proveedores activos: un proveedor que no esta
-- habilitado nunca puede estar activo (check de abajo).

alter table ${schema}.proveedor
  add column trade_name           varchar(220),
  add column address              varchar(250),
  add column city                 varchar(120),
  add column phone                varchar(30),
  add column website              varchar(200),
  add column contact_role         varchar(120),
  add column offering_description varchar(1000),
  add column payment_condition    varchar(150),
  add column credit_days          integer check (credit_days is null or credit_days between 0 and 365),
  add column lead_days            integer check (lead_days is null or lead_days between 0 and 365),
  add column warranty_terms       varchar(500),
  -- Los proveedores que ya existen operan en Compras: quedan habilitados.
  add column status               varchar(30) not null default 'HABILITADO'
    check (status in ('BORRADOR', 'PENDIENTE_REVISION', 'HABILITADO', 'OBSERVADO', 'RECHAZADO')),
  add column status_reason        varchar(1000),
  add column submitted_at         timestamptz,
  add column decided_at           timestamptz,
  add column decided_by           uuid references ${schema}.usuario(user_id),
  add column created_at           timestamptz not null default now(),
  add column created_by           uuid references ${schema}.usuario(user_id),
  add column updated_at           timestamptz;

alter table ${schema}.proveedor
  add constraint proveedor_activo_habilitado_check check (not active or status = 'HABILITADO');

comment on column ${schema}.proveedor.status is
  'Ciclo de incorporacion (DEC-L069). Solo HABILITADO puede estar activo; activo/inactivo sigue siendo lo que lee Compras.';

-- Categorias que atiende el proveedor (DEC-L069): reutiliza la clasificacion
-- del catalogo. family_id nulo = toda la familia (tipo de producto).
create table ${schema}.proveedor_categoria (
  supplier_category_id uuid primary key default gen_random_uuid(),
  supplier_id     uuid not null references ${schema}.proveedor(supplier_id),
  product_type_id uuid not null references ${schema}.tipo_producto(product_type_id),
  family_id       uuid references ${schema}.familia_producto(family_id),
  created_by      uuid references ${schema}.usuario(user_id),
  created_at      timestamptz not null default now()
);

create unique index ux_proveedor_categoria
  on ${schema}.proveedor_categoria (supplier_id, product_type_id, coalesce(family_id, '00000000-0000-0000-0000-000000000000'::uuid));
create index idx_proveedor_categoria_tipo on ${schema}.proveedor_categoria (product_type_id, family_id);

-- Historial de decisiones de la ficha: se lee de log_cambios por entidad.
create index if not exists idx_log_cambios_entidad on ${schema}.log_cambios (entidad, entidad_id, created_at);
