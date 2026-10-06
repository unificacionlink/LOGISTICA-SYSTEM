-- V5__stock.sql
-- Lote 3: modulo Verificacion de Stock. Basada en
-- db_design_reference/migrations/0011_inventario_almacen.sql +
-- 0016_reserva_stock.sql, sin RLS (DEC-L008) y sin fn_saldo_stock (el saldo
-- se calcula en Java, en StockService, siguiendo el mismo patron que el
-- resto del backend).

create table ${schema}.almacen (
  warehouse_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references ${schema}.empresa(company_id),
  code         varchar(30) not null,
  name         varchar(160) not null,
  warehouse_type varchar(30) not null default 'CENTRAL'
    check (warehouse_type in ('CENTRAL','SUBALMACEN','PROVEEDOR')),
  active       boolean not null default true,
  unique (company_id, code)
);

comment on table ${schema}.almacen is 'Almacenes y subalmacenes. Lote 3 solo usa un CENTRAL por empresa.';

create table ${schema}.movimiento_inventario (
  inventory_movement_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references ${schema}.empresa(company_id),
  warehouse_id uuid not null references ${schema}.almacen(warehouse_id),
  product_id   uuid not null references ${schema}.producto(product_id),
  variant_id   uuid references ${schema}.producto_variante(variant_id),
  movement_type varchar(40) not null
    check (movement_type in ('ENTRADA','SALIDA','RESERVA','AJUSTE','PRESTAMO','DEVOLUCION')),
  qty          numeric(14,3) not null,  -- con signo: entradas positivas, salidas negativas
  unit_cost    numeric(14,4),
  reference_type varchar(40) not null check (reference_type in ('OC','RECEPCION','SALIDA','AJUSTE')),
  reference_id uuid not null,
  created_by   uuid not null references ${schema}.usuario(user_id),
  created_at   timestamptz not null default now()
);

comment on table ${schema}.movimiento_inventario is
  'Kardex: fuente de verdad del inventario (RN-001). Solo INSERT. Lote 3 solo '
  'genera movimientos AJUSTE (carga/correccion manual de LOGISTICA/ADMIN); '
  'ENTRADA por recepcion contra OC queda para el modulo Almacen (Fase 2).';

create index idx_movimiento_inventario_saldo
  on ${schema}.movimiento_inventario (company_id, warehouse_id, product_id, variant_id);

create table ${schema}.reserva_stock (
  reservation_id uuid primary key default gen_random_uuid(),
  request_item_id uuid not null references ${schema}.requerimiento_item(request_item_id),
  warehouse_id uuid not null references ${schema}.almacen(warehouse_id),
  product_id   uuid not null references ${schema}.producto(product_id),
  variant_id   uuid references ${schema}.producto_variante(variant_id),
  qty_reserved numeric(14,3) not null check (qty_reserved > 0),
  status       varchar(30) not null default 'RESERVADA'
    check (status in ('RESERVADA','DESPACHADA','LIBERADA')),
  reserved_at  timestamptz not null default now()
);

comment on table ${schema}.reserva_stock is
  'Reserva por item de requerimiento, creada al confirmar la verificacion de '
  'stock. El despacho fisico (status DESPACHADA) es modulo Almacen, Fase 2.';

create index idx_reserva_stock_producto
  on ${schema}.reserva_stock (warehouse_id, product_id, variant_id) where status = 'RESERVADA';

-- Un almacen CENTRAL por empresa.
insert into ${schema}.almacen (company_id, code, name)
  select company_id, 'CENTRAL', 'Almacen Central'
  from ${schema}.empresa
  on conflict (company_id, code) do nothing;
