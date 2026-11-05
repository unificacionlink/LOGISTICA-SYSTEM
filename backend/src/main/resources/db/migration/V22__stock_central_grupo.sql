-- ============================================================
-- V22 — Stock central del grupo, inventario por Excel y stock minimo (DEC-L045)
-- Todo el stock lo maneja TDEM: un solo almacen central atiende a TDEM,
-- GOYDEL y METRIN (reservas, recepciones, despachos y caja chica).
-- ============================================================

alter table ${schema}.almacen add column if not exists is_group_central boolean not null default false;

update ${schema}.almacen a
   set is_group_central = true
  from ${schema}.empresa e
 where e.company_id = a.company_id
   and e.code = 'TDEMSRL'
   and a.warehouse_type = 'CENTRAL'
   and a.active;

create unique index if not exists uq_almacen_central_grupo
  on ${schema}.almacen ((true)) where is_group_central;

comment on column ${schema}.almacen.is_group_central is
  'DEC-L045: almacen central unico del grupo (el de TDEM). Lo usan las tres empresas.';

-- Los centrales de las otras empresas quedan inactivos si nunca se usaron.
update ${schema}.almacen a
   set active = false
 where a.warehouse_type = 'CENTRAL'
   and not a.is_group_central
   and not exists (select 1 from ${schema}.movimiento_inventario m where m.warehouse_id = a.warehouse_id)
   and not exists (select 1 from ${schema}.reserva_stock r where r.warehouse_id = a.warehouse_id);

-- Motivo de ajustes y referencia a las cargas de inventario.
alter table ${schema}.movimiento_inventario add column if not exists notes varchar(300);

alter table ${schema}.movimiento_inventario drop constraint if exists movimiento_inventario_reference_type_check;
alter table ${schema}.movimiento_inventario
  add constraint movimiento_inventario_reference_type_check
    check (reference_type in ('OC','RECEPCION','SALIDA','AJUSTE','CAJA_CHICA','CARGA_STOCK'));

create index if not exists idx_movimiento_inventario_kardex
  on ${schema}.movimiento_inventario (warehouse_id, variant_id, created_at);

-- Inventario fisico por Excel: una fila por carga, cada diferencia es un AJUSTE.
create table if not exists ${schema}.carga_stock (
  stock_load_id    uuid primary key default gen_random_uuid(),
  load_number      integer not null unique,
  code             varchar(30) not null unique,      -- INV-<n>
  warehouse_id     uuid not null references ${schema}.almacen(warehouse_id),
  file_name        varchar(220),
  rows_counted     integer not null check (rows_counted >= 0),
  adjustments      integer not null check (adjustments >= 0),
  minimums_changed integer not null check (minimums_changed >= 0),
  created_by       uuid not null references ${schema}.usuario(user_id),
  created_at       timestamptz not null default now()
);

comment on table ${schema}.carga_stock is
  'DEC-L045: carga de inventario fisico por Excel. Sus movimientos AJUSTE llevan reference_type CARGA_STOCK.';

-- Stock minimo por SKU (variante) en el almacen central.
create table if not exists ${schema}.stock_minimo (
  warehouse_id uuid not null references ${schema}.almacen(warehouse_id),
  variant_id   uuid not null references ${schema}.producto_variante(variant_id),
  min_qty      numeric(14,3) not null check (min_qty >= 0),
  updated_by   uuid references ${schema}.usuario(user_id),
  updated_at   timestamptz not null default now(),
  primary key (warehouse_id, variant_id)
);

comment on table ${schema}.stock_minimo is
  'DEC-L045: stock minimo por SKU. Bajo este valor el SKU se marca para reponer.';
