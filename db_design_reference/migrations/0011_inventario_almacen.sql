-- 0011_inventario_almacen.sql
-- ALMACEN, POLITICA_STOCK, MOVIMIENTO_INVENTARIO (subset de INVENTARIO para
-- "verificar stock"; RECEPCION/SALIDA fisicas del modulo Almacen completo
-- quedan en Fase 2). RESERVA_STOCK se crea en 0016 (depende de REQUERIMIENTO_ITEM).
--
-- RN-001 (Excel 05_Reglas_Negocio): el saldo NUNCA se edita directo, se deriva
-- de MOVIMIENTO_INVENTARIO. Por eso no existe una columna "stock" en ningun
-- lado: solo la funcion fn_saldo_stock() (redefinida en 0016 para descontar
-- reservas activas una vez que RESERVA_STOCK existe).

create table if not exists public.almacen (
  warehouse_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references public.empresa(company_id),
  code         varchar(30) not null,
  name         varchar(160) not null,
  warehouse_type varchar(30) not null default 'CENTRAL'
    check (warehouse_type in ('CENTRAL','SUBALMACEN','PROVEEDOR')),
  active       boolean not null default true,
  unique (company_id, code)
);

comment on table public.almacen is 'Almacenes y subalmacenes.';

create table if not exists public.politica_stock (
  stock_policy_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references public.empresa(company_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  min_stock       numeric(14,3) not null default 0,
  target_stock    numeric(14,3) not null default 0,
  reorder_point   numeric(14,3),
  auto_replenishment boolean not null default false,
  rotation_class  varchar(30) not null default 'BAJA' check (rotation_class in ('ALTA','BAJA','EVENTUAL')),
  effective_from  date not null default current_date,
  effective_to    date,
  unique (company_id, product_id, coalesce(variant_id, '00000000-0000-0000-0000-000000000000'), effective_from)
);

comment on table public.politica_stock is 'Stock minimo/objetivo y automatismo por empresa y variante.';
comment on column public.politica_stock.auto_replenishment is
  'RN-003: el automatismo solo aplica si ademas existe convenio/precio fijo vigente (verificado en el service, no aqui).';

create table if not exists public.movimiento_inventario (
  inventory_movement_id uuid primary key default gen_random_uuid(),
  company_id   uuid not null references public.empresa(company_id),
  warehouse_id uuid not null references public.almacen(warehouse_id),
  product_id   uuid not null references public.producto(product_id),
  variant_id   uuid references public.producto_variante(variant_id),
  movement_type varchar(40) not null
    check (movement_type in ('ENTRADA','SALIDA','RESERVA','AJUSTE','PRESTAMO','DEVOLUCION')),
  qty          numeric(14,3) not null,  -- con signo: entradas positivas, salidas negativas
  unit_cost    numeric(14,4),
  reference_type varchar(40) not null check (reference_type in ('OC','RECEPCION','SALIDA','AJUSTE')),
  reference_id uuid not null,
  created_by   uuid not null references public.usuario(user_id),
  created_at   timestamptz not null default now()
);

comment on table public.movimiento_inventario is
  'Kardex: fuente de verdad del inventario (RN-001). Solo INSERT, nunca UPDATE/DELETE de saldo.';

create index if not exists idx_movimiento_inventario_saldo
  on public.movimiento_inventario (company_id, warehouse_id, product_id, variant_id);

-- Ninguna columna de saldo es editable: solo permitimos INSERT sobre el kardex.
revoke update, delete on public.movimiento_inventario from authenticated;

create or replace function public.fn_saldo_stock(
  p_company_id uuid,
  p_warehouse_id uuid,
  p_product_id uuid,
  p_variant_id uuid default null
)
returns numeric
language sql stable
as $$
  select coalesce(sum(qty), 0)
  from public.movimiento_inventario
  where company_id = p_company_id
    and warehouse_id = p_warehouse_id
    and product_id = p_product_id
    and (variant_id is not distinct from p_variant_id);
$$;

comment on function public.fn_saldo_stock(uuid, uuid, uuid, uuid) is
  'Saldo fisico derivado del kardex (sin descontar reservas). Redefinida en 0016_reserva_stock.sql '
  'para descontar reservas activas una vez que existe RESERVA_STOCK -- esa es la version a usar '
  'para "stock disponible" (RN-001/RN-002).';
